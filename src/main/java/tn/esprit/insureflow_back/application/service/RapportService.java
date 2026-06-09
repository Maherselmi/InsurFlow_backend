package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.domain.model.AgentResult;
import tn.esprit.insureflow_back.domain.model.Claim;

/**
 * Service responsable de la génération du rapport expert.
 * Ce rapport synthétise le dossier, les résultats des agents IA et la décision finale.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RapportService {

    /*
     * Génère un rapport de synthèse pour un sinistre donné.
     * Le rapport regroupe le résumé du dossier, les analyses IA et la recommandation.
     */
    public String genererRapport(
            Claim claim,
            AgentResult routeResult,
            AgentResult validationResult,
            AgentResult estimationResult
    ) {
        if (claim == null) {
            throw new IllegalArgumentException("Claim obligatoire");
        }

        /*
         * Récupère le statut actuel du sinistre.
         * Si le statut est absent, une valeur par défaut est utilisée.
         */
        String statut = claim.getStatus() != null
                ? claim.getStatus().name()
                : "INCONNU";

        log.info("Génération rapport expert - claim #{} statut: {}", claim.getId(), statut);

        /*
         * Récupère les conclusions produites par chaque agent IA.
         */
        String routeConclusion = routeResult != null
                ? safe(routeResult.getConclusion())
                : "Information non disponible";

        String validationConclusion = validationResult != null
                ? safe(validationResult.getConclusion())
                : "Information non disponible";

        String estimationConclusion = estimationResult != null
                ? safe(estimationResult.getConclusion())
                : "Information non disponible";

        /*
         * Récupère les scores de confiance de chaque agent IA.
         */
        String routeConfidence = routeResult != null
                ? String.valueOf(routeResult.getConfidenceScore())
                : "Information non disponible";

        String validationConfidence = validationResult != null
                ? String.valueOf(validationResult.getConfidenceScore())
                : "Information non disponible";

        String estimationConfidence = estimationResult != null
                ? String.valueOf(estimationResult.getConfidenceScore())
                : "Information non disponible";

        /*
         * Construction des différentes parties du rapport.
         */
        String resume = buildResume(claim, statut);
        String vigilance = buildPointsVigilance(routeResult, validationResult, estimationResult, statut);
        String recommendation = buildRecommendation(statut, validationConclusion, estimationConclusion);
        String humanDecision = buildHumanDecision(statut);

        /*
         * Assemblage final du rapport expert.
         */
        return """
                Rapport de synthèse du sinistre n°%s

                1. Résumé du dossier
                %s

                2. Analyse des agents
                Agent routeur : %s | Confiance : %s
                Agent validateur : %s | Confiance : %s
                Agent estimateur : %s | Confiance : %s

                3. Points de vigilance
                %s

                4. Recommandation finale
                %s

                5. Décision humaine
                Statut : %s
                Commentaire : %s
                """
                .formatted(
                        claim.getId(),
                        resume,
                        routeConclusion,
                        routeConfidence,
                        validationConclusion,
                        validationConfidence,
                        estimationConclusion,
                        estimationConfidence,
                        vigilance,
                        recommendation,
                        statut,
                        humanDecision
                )
                .trim();
    }

    /*
     * Construit le résumé principal du dossier.
     */
    private String buildResume(Claim claim, String statut) {
        return """
                Sinistre déclaré sous le dossier %s, description : %s, date du sinistre : %s.
                Statut actuel du dossier : %s.
                """.formatted(
                claim.getId(),
                safe(claim.getDescription()),
                claim.getIncidentDate() != null
                        ? claim.getIncidentDate().toString()
                        : "Information non disponible",
                statut
        ).trim();
    }

    /*
     * Identifie les points nécessitant une attention humaine.
     */
    private String buildPointsVigilance(
            AgentResult routeResult,
            AgentResult validationResult,
            AgentResult estimationResult,
            String statut
    ) {
        StringBuilder sb = new StringBuilder();

        /*
         * Vérifie si l’agent routeur demande une revue humaine.
         */
        if (routeResult != null && routeResult.isNeedsHumanReview()) {
            sb.append("Le routage nécessite une revue humaine. ");
        }

        /*
         * Vérifie si l’agent validateur signale une incertitude.
         */
        if (validationResult != null && validationResult.isNeedsHumanReview()) {
            sb.append("La validation contractuelle comporte une incertitude ou une ambiguïté. ");
        }

        /*
         * Vérifie si l’estimation financière doit être confirmée.
         */
        if (estimationResult != null && estimationResult.isNeedsHumanReview()) {
            sb.append("L’estimation financière doit être confirmée manuellement. ");
        }

        /*
         * Ajoute un point de vigilance si le dossier attend encore une décision humaine.
         */
        if ("PENDING_VALIDATION".equals(statut)) {
            sb.append("Le dossier reste en attente de validation humaine complémentaire. ");
        }

        /*
         * Si aucun risque particulier n’est détecté, un message standard est ajouté.
         */
        if (sb.isEmpty()) {
            sb.append("Aucun point de vigilance majeur identifié à ce stade.");
        }

        return sb.toString().trim();
    }

    /*
     * Construit la recommandation finale selon le statut du dossier.
     */
    private String buildRecommendation(
            String statut,
            String validationConclusion,
            String estimationConclusion
    ) {
        return switch (statut) {
            case "APPROVED" -> """
                    Recommandation : poursuivre le traitement du dossier sur la base d’une couverture confirmée.
                    Estimation disponible : %s.
                    """.formatted(estimationConclusion).trim();

            case "REJECTED" -> """
                    Recommandation : clôturer le dossier avec motif principal de non-couverture.
                    Résultat validation : %s.
                    """.formatted(validationConclusion).trim();

            case "PENDING_VALIDATION" -> """
                    Recommandation : transmettre le dossier à un gestionnaire ou expert pour décision finale.
                    Résultat validation : %s.
                    """.formatted(validationConclusion).trim();

            default -> "Recommandation : poursuivre l’analyse du dossier.";
        };
    }

    /*
     * Génère le commentaire lié à la décision humaine.
     */
    private String buildHumanDecision(String statut) {
        return switch (statut) {
            case "APPROVED" -> "Décision finale orientée vers l’approbation du dossier.";
            case "REJECTED" -> "Décision finale orientée vers le rejet du dossier.";
            case "PENDING_VALIDATION" -> "Décision différée dans l’attente d’une validation humaine.";
            default -> "Décision non stabilisée.";
        };
    }

    /*
     * Sécurise une chaîne null ou vide avec un message par défaut.
     */
    private String safe(String value) {
        return value == null || value.isBlank()
                ? "Information non disponible"
                : value.trim();
    }
}