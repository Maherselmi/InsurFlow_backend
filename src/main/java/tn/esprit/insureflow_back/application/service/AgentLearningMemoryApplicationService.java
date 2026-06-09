package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.domain.enums.AgentName;
import tn.esprit.insureflow_back.domain.model.AgentLearningFeedback;
import tn.esprit.insureflow_back.domain.port.out.AgentLearningFeedbackRepositoryPort;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service applicatif responsable de construire un bloc mémoire pour un agent IA.
 *
 * Ce bloc mémoire est généré à partir des feedbacks experts déjà enregistrés.
 * Il permet à l’agent de tenir compte :
 * - des corrections faites par l’expert ;
 * - des exemples validés par l’expert ;
 * - des anciens cas similaires utilisés pour l’apprentissage.
 *
 * Le résultat final est une chaîne de caractères qui peut être injectée dans le prompt
 * de l’agent afin d’améliorer ses futures décisions.
 */
@Service
@RequiredArgsConstructor
public class AgentLearningMemoryApplicationService {

    /**
     * Taille maximale autorisée pour les champs longs.
     *
     * Cette limite évite d’envoyer trop de texte dans le prompt du LLM,
     * ce qui permet de réduire le coût, améliorer la lisibilité et éviter
     * de dépasser la limite de tokens.
     */
    private static final int MAX_FIELD_CHARS = 900;

    /**
     * Nombre maximal de corrections expertes à inclure dans le bloc mémoire.
     *
     * Les corrections sont prioritaires car elles indiquent directement
     * les erreurs précédentes de l’agent et comment les éviter.
     */
    private static final int MAX_CORRECTIONS = 3;

    /**
     * Nombre maximal d’exemples validés à inclure dans le bloc mémoire.
     *
     * Ces exemples servent de référence positive pour montrer à l’agent
     * ce qu’un bon résultat ressemble.
     */
    private static final int MAX_VALIDATED = 2;

    /**
     * Port de sortie permettant d’accéder aux feedbacks d’apprentissage.
     *
     * Le service applicatif ne dépend pas directement d’une technologie de persistance.
     * Il utilise une interface du domaine afin de respecter une architecture propre
     * de type hexagonale / clean architecture.
     */
    private final AgentLearningFeedbackRepositoryPort feedbackRepositoryPort;

    /**
     * Construit le bloc mémoire destiné à un agent donné.
     *
     * Le bloc mémoire est construit à partir des anciens feedbacks experts,
     * en excluant le dossier courant pour éviter que l’agent ne réutilise
     * directement les données du cas en cours.
     *
     * @param agentName nom de l’agent concerné
     * @param currentClaimId identifiant du sinistre courant à exclure
     * @return bloc mémoire formaté sous forme de texte
     */
    public String buildMemoryBlock(AgentName agentName, Long currentClaimId) {

        /*
         * Récupération des exemples d’apprentissage depuis le repository.
         *
         * On récupère au maximum 20 exemples liés au même agent,
         * en excluant le sinistre courant.
         *
         * Ces exemples seront ensuite filtrés pour séparer :
         * - les corrections expertes ;
         * - les sorties validées par l’expert.
         */
        List<AgentLearningFeedback> examples =
                feedbackRepositoryPort.findLearningExamples(
                        agentName,
                        currentClaimId,
                        PageRequest.of(0, 20)
                );

        /*
         * Sélection des corrections expertes.
         *
         * Une correction correspond à un feedback où :
         * - le feedback est exploitable ;
         * - l’expert a indiqué que la sortie initiale de l’agent était incorrecte.
         *
         * Les résultats sont triés du plus récent au plus ancien, puis limités
         * au nombre maximal défini par MAX_CORRECTIONS.
         */
        List<AgentLearningFeedback> corrections = examples.stream()
                .filter(this::isUsable)
                .filter(f -> Boolean.FALSE.equals(f.getWasCorrect()))
                .sorted(Comparator.comparing(
                        AgentLearningFeedback::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(MAX_CORRECTIONS)
                .collect(Collectors.toList());

        /*
         * Sélection des exemples validés par l’expert.
         *
         * Un exemple validé correspond à un feedback où :
         * - le feedback est exploitable ;
         * - l’expert a confirmé que la sortie de l’agent était correcte.
         *
         * Ces exemples servent à montrer à l’agent les bons comportements
         * à reproduire dans les prochains cas.
         */
        List<AgentLearningFeedback> validated = examples.stream()
                .filter(this::isUsable)
                .filter(f -> Boolean.TRUE.equals(f.getWasCorrect()))
                .sorted(Comparator.comparing(
                        AgentLearningFeedback::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(MAX_VALIDATED)
                .collect(Collectors.toList());

        /*
         * Construction progressive du bloc mémoire.
         *
         * StringBuilder est utilisé ici car plusieurs morceaux de texte
         * sont concaténés dynamiquement.
         */
        StringBuilder block = new StringBuilder();

        /*
         * Ajout des corrections expertes dans le bloc mémoire.
         *
         * Cette section est placée en premier car les corrections sont plus importantes :
         * elles indiquent clairement les erreurs que l’agent doit éviter.
         */
        if (!corrections.isEmpty()) {
            block.append("=== CORRECTIONS EXPERTES A APPLIQUER ===\n");
            block.append("IMPORTANT : Ces cas ont été corrigés par l'expert. ")
                    .append("Adapte ton analyse en conséquence.\n\n");

            corrections.stream()
                    .map(this::formatExample)
                    .forEach(s -> block.append(s).append("\n\n"));
        }

        /*
         * Ajout des exemples validés par l’expert.
         *
         * Ces exemples ne corrigent pas forcément une erreur,
         * mais ils servent de modèle positif pour guider l’agent.
         */
        if (!validated.isEmpty()) {
            block.append("=== EXEMPLES VALIDES PAR EXPERT ===\n\n");

            validated.stream()
                    .map(this::formatExample)
                    .forEach(s -> block.append(s).append("\n\n"));
        }

        /*
         * Retour du bloc mémoire final.
         *
         * trim() permet de supprimer les espaces et sauts de ligne inutiles
         * au début et à la fin du texte.
         */
        return block.toString().trim();
    }

    /**
     * Vérifie si un feedback peut être utilisé pour l’apprentissage.
     *
     * Un feedback est considéré comme utilisable si :
     * - il n’est pas null ;
     * - il est marqué comme utilisable pour l’apprentissage ;
     * - il contient une sortie finale validée par l’expert.
     *
     * @param feedback feedback à vérifier
     * @return true si le feedback est exploitable, false sinon
     */
    private boolean isUsable(AgentLearningFeedback feedback) {
        return feedback != null
                && Boolean.TRUE.equals(feedback.getUseForLearning())
                && hasText(feedback.getFinalValidatedOutput());
    }

    /**
     * Formate un feedback sous forme de texte lisible pour le prompt.
     *
     * Le format contient :
     * - le type d’exemple ;
     * - l’état de correction de la sortie agent ;
     * - la satisfaction de l’expert ;
     * - le commentaire expert ;
     * - les données d’entrée du dossier ;
     * - la sortie initiale de l’agent ;
     * - la sortie finale validée par l’expert.
     *
     * Les champs longs sont tronqués pour éviter de générer un bloc mémoire trop volumineux.
     *
     * @param feedback feedback à formater
     * @return texte formaté représentant un exemple d’apprentissage
     */
    private String formatExample(AgentLearningFeedback feedback) {
        /*
         * Détermination du type d’exemple.
         *
         * Si la sortie de l’agent était correcte, l’exemple est considéré
         * comme un exemple validé.
         *
         * Sinon, il est considéré comme une correction experte à apprendre.
         */
        boolean correct = Boolean.TRUE.equals(feedback.getWasCorrect());
        String label = correct
                ? "EXEMPLE VALIDE PAR EXPERT"
                : "CORRECTION EXPERT A APPRENDRE";

        /*
         * Construction du texte final de l’exemple.
         *
         * Les valeurs nulles sont sécurisées, et les champs volumineux
         * sont limités avec la méthode truncate().
         */
        return """
                %s
                Resultat agent correct : %s
                Satisfaction expert : %s/5
                Commentaire expert : %s

                Entree dossier :
                %s

                Sortie agent initiale :
                %s

                Sortie finale validee par expert :
                %s
                """.formatted(
                label,
                correct ? "OUI" : "NON",
                feedback.getSatisfactionScore() == null
                        ? "N/A"
                        : feedback.getSatisfactionScore().toString(),
                truncate(feedback.getExpertComment(), 250),
                truncate(feedback.getInputData(), MAX_FIELD_CHARS),
                truncate(feedback.getAgentOutput(), MAX_FIELD_CHARS),
                truncate(feedback.getFinalValidatedOutput(), MAX_FIELD_CHARS)
        ).trim();
    }

    /**
     * Vérifie si une chaîne de caractères contient réellement du texte.
     *
     * Une chaîne null, vide ou composée uniquement d’espaces est considérée
     * comme non valide.
     *
     * @param value chaîne à tester
     * @return true si la chaîne contient du texte, false sinon
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Tronque une chaîne de caractères si elle dépasse une taille maximale.
     *
     * Cette méthode est utilisée pour limiter la taille du bloc mémoire envoyé au LLM.
     * Elle évite aussi les erreurs liées aux valeurs nulles.
     *
     * @param value texte à tronquer
     * @param max nombre maximal de caractères autorisés
     * @return texte original ou texte tronqué avec "..."
     */
    private String truncate(String value, int max) {
        /*
         * Sécurisation de la valeur.
         *
         * Si la valeur est null, on retourne une chaîne vide.
         * Sinon, on supprime les espaces inutiles au début et à la fin.
         */
        String safe = value == null ? "" : value.trim();

        /*
         * Si le texte ne dépasse pas la taille maximale,
         * il est retourné tel quel.
         */
        if (safe.length() <= max) {
            return safe;
        }

        /*
         * Si le texte dépasse la taille maximale,
         * on garde uniquement les premiers caractères autorisés
         * puis on ajoute "..." pour indiquer que le contenu a été coupé.
         */
        return safe.substring(0, max) + "...";
    }
}