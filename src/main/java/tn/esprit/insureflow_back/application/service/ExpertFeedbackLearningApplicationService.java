package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.insureflow_back.application.dto.ExpertFeedbackRequest;
import tn.esprit.insureflow_back.domain.enums.AgentName;
import tn.esprit.insureflow_back.domain.model.AgentLearningFeedback;
import tn.esprit.insureflow_back.domain.model.Claim;
import tn.esprit.insureflow_back.domain.model.Policy;
import tn.esprit.insureflow_back.domain.port.out.AgentLearningFeedbackRepositoryPort;
import tn.esprit.insureflow_back.domain.port.out.ClaimRepositoryPort;

/**
 * Service responsable d’enregistrer les feedbacks experts
 * pour améliorer l’apprentissage des agents IA.
 */
@Service
@RequiredArgsConstructor
public class ExpertFeedbackLearningApplicationService {

    /*
     * Ports utilisés pour récupérer les sinistres
     * et sauvegarder les feedbacks d’apprentissage.
     */
    private final ClaimRepositoryPort claimRepositoryPort;
    private final AgentLearningFeedbackRepositoryPort learningRepositoryPort;

    /*
     * Sauvegarde les corrections ou validations données par l’expert.
     * Chaque agent peut recevoir son propre feedback.
     */
    @Transactional
    public int saveExpertFeedback(ExpertFeedbackRequest request) {
        validateRequest(request);

        /*
         * Récupération du sinistre concerné par le feedback expert.
         */
        Claim claim = claimRepositoryPort.findById(request.getClaimId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Claim not found: " + request.getClaimId()
                ));

        int saved = 0;

        /*
         * Sauvegarde du feedback pour l’agent routeur
         * si l’expert a fourni un type final.
         */
        if (hasText(request.getFinalType())) {
            upsertLearningFeedback(
                    claim,
                    AgentName.AGENT_ROUTEUR,
                    buildCommonInput(claim),
                    buildRouteurAgentOutput(request),
                    normalizeType(request.getFinalType()),
                    request.getRouteurCorrect(),
                    request.getRouteurConfidence(),
                    mergeComments(request.getGlobalComment(), request.getRouteurComment()),
                    request
            );
            saved++;
        }

        /*
         * Sauvegarde du feedback pour l’agent de validation
         * si l’expert a fourni une décision finale.
         */
        if (hasText(request.getFinalDecision())) {
            upsertLearningFeedback(
                    claim,
                    AgentName.AGENT_VALIDATION,
                    buildValidationInput(claim, request),
                    buildValidationAgentOutput(request),
                    normalizeDecision(request.getFinalDecision()),
                    request.getValidationCorrect(),
                    request.getValidationConfidence(),
                    mergeComments(request.getGlobalComment(), request.getValidationComment()),
                    request
            );
            saved++;
        }

        /*
         * Sauvegarde du feedback pour l’agent estimateur
         * si l’expert a fourni une estimation finale complète.
         */
        if (hasFinalEstimate(request)) {
            upsertLearningFeedback(
                    claim,
                    AgentName.AGENT_ESTIMATEUR,
                    buildEstimateurInput(claim, request),
                    buildEstimateurAgentOutput(request),
                    buildFinalEstimateOutput(request),
                    resolveEstimateurCorrect(request),
                    request.getEstimateurConfidence(),
                    mergeComments(request.getGlobalComment(), request.getEstimateurComment()),
                    request
            );
            saved++;
        }

        return saved;
    }

    /*
     * Crée ou met à jour un feedback d’apprentissage
     * pour un sinistre et un agent donné.
     */
    private void upsertLearningFeedback(
            Claim claim,
            AgentName agentName,
            String inputData,
            String agentOutput,
            String finalValidatedOutput,
            Boolean wasCorrect,
            Double predictedConfidence,
            String expertComment,
            ExpertFeedbackRequest request
    ) {
        AgentLearningFeedback feedback = learningRepositoryPort
                .findByClaimIdAndAgentName(claim.getId(), agentName)
                .orElseGet(AgentLearningFeedback::new);

        feedback.setClaim(claim);
        feedback.setAgentName(agentName);
        feedback.setInputData(inputData);
        feedback.setAgentOutput(agentOutput);
        feedback.setFinalValidatedOutput(finalValidatedOutput);
        feedback.setWasCorrect(Boolean.TRUE.equals(wasCorrect));
        feedback.setUseForLearning(Boolean.TRUE.equals(request.getUseForLearning()));
        feedback.setPredictedConfidence(predictedConfidence);
        feedback.setReviewedBy(safe(request.getReviewedBy()));
        feedback.setExpertComment(expertComment);
        feedback.setSatisfactionScore(normalizeSatisfaction(request.getSatisfactionScore()));

        learningRepositoryPort.save(feedback);
    }

    /*
     * Vérifie que la requête de feedback contient les données nécessaires.
     */
    private void validateRequest(ExpertFeedbackRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Feedback request is required");
        }

        if (request.getClaimId() == null) {
            throw new IllegalArgumentException("claimId is required");
        }

        if (!hasText(request.getFinalType())
                && !hasText(request.getFinalDecision())
                && !hasFinalEstimate(request)) {
            throw new IllegalArgumentException("At least one final expert answer is required");
        }
    }

    /*
     * Construit les données communes du sinistre
     * utilisées comme entrée pour les agents IA.
     */
    private String buildCommonInput(Claim claim) {
        StringBuilder sb = new StringBuilder();

        sb.append("Claim ID: ").append(claim.getId()).append('\n');
        sb.append("Description: ").append(safe(claim.getDescription())).append('\n');
        sb.append("Incident date: ")
                .append(claim.getIncidentDate() == null ? "N/A" : claim.getIncidentDate())
                .append('\n');
        sb.append(buildPolicyLine(claim));

        return sb.toString().trim();
    }

    /*
     * Construit l’entrée utilisée pour l’agent de validation.
     */
    private String buildValidationInput(Claim claim, ExpertFeedbackRequest request) {
        return buildCommonInput(claim)
                + "\nPredicted routeur type: " + safe(request.getPredictedType())
                + "\nFinal routeur type: " + safe(request.getFinalType());
    }

    /*
     * Construit l’entrée utilisée pour l’agent estimateur.
     */
    private String buildEstimateurInput(Claim claim, ExpertFeedbackRequest request) {
        int documentsCount = claim.getDocuments() == null
                ? 0
                : claim.getDocuments().size();

        return buildCommonInput(claim)
                + "\nFinal validation decision: " + safe(request.getFinalDecision())
                + "\nDocuments count: " + documentsCount;
    }

    /*
     * Formate les informations de la police liée au sinistre.
     */
    private String buildPolicyLine(Claim claim) {
        Policy policy = claim.getPolicy();

        if (policy == null) {
            return "Policy: N/A";
        }

        return "Policy: number=" + safe(policy.getPolicyNumber())
                + ", type=" + safe(policy.getType())
                + ", formule=" + safe(policy.getFormule())
                + ", productCode=" + safe(policy.getProductCode())
                + ", start=" + (policy.getStartDate() == null ? "N/A" : policy.getStartDate())
                + ", end=" + (policy.getEndDate() == null ? "N/A" : policy.getEndDate());
    }

    /*
     * Formate la sortie produite par l’agent routeur.
     */
    private String buildRouteurAgentOutput(ExpertFeedbackRequest request) {
        StringBuilder sb = new StringBuilder();

        sb.append("Predicted type: ").append(safe(request.getPredictedType()));
        sb.append(" | confidence: ").append(safeDouble(request.getRouteurConfidence()));

        if (hasText(request.getRouteurJustification())) {
            sb.append("\nJustification IA:\n")
                    .append(request.getRouteurJustification().trim());
        }

        return sb.toString();
    }

    /*
     * Formate la sortie produite par l’agent de validation.
     */
    private String buildValidationAgentOutput(ExpertFeedbackRequest request) {
        StringBuilder sb = new StringBuilder();

        sb.append("Predicted decision: ").append(safe(request.getPredictedDecision()));
        sb.append(" | confidence: ").append(safeDouble(request.getValidationConfidence()));

        if (hasText(request.getValidationJustification())) {
            sb.append("\nJustification IA:\n")
                    .append(request.getValidationJustification().trim());
        }

        return sb.toString();
    }

    /*
     * Formate la sortie produite par l’agent estimateur.
     */
    private String buildEstimateurAgentOutput(ExpertFeedbackRequest request) {
        StringBuilder sb = new StringBuilder();

        sb.append("Predicted estimation min: ")
                .append(safeDouble(request.getPredictedEstimationMin()));
        sb.append(" | moyenne: ")
                .append(safeDouble(request.getPredictedEstimationMoyenne()));
        sb.append(" | max: ")
                .append(safeDouble(request.getPredictedEstimationMax()));
        sb.append(" | confidence: ")
                .append(safeDouble(request.getEstimateurConfidence()));
        sb.append(" | evaluation: ")
                .append(safe(request.getEstimateEvaluation()));

        if (hasText(request.getEstimateurJustification())) {
            sb.append("\nJustification IA:\n")
                    .append(request.getEstimateurJustification().trim());
        }

        return sb.toString();
    }

    /*
     * Formate l’estimation finale validée par l’expert.
     */
    private String buildFinalEstimateOutput(ExpertFeedbackRequest request) {
        return "Final estimation min: " + safeDouble(request.getFinalEstimationMin())
                + " | moyenne: " + safeDouble(request.getFinalEstimationMoyenne())
                + " | max: " + safeDouble(request.getFinalEstimationMax());
    }

    /*
     * Détermine si l’estimation de l’agent est correcte.
     */
    private Boolean resolveEstimateurCorrect(ExpertFeedbackRequest request) {
        if (request.getEstimateurCorrect() != null) {
            return request.getEstimateurCorrect();
        }

        String evaluation = safe(request.getEstimateEvaluation()).toUpperCase();

        if (evaluation.contains("INCORRECT")
                || evaluation.contains("SOUS")
                || evaluation.contains("SUR")
                || evaluation.contains("SOUS_ESTIME")
                || evaluation.contains("SUR_ESTIME")) {
            return false;
        }

        if (evaluation.contains("CORRECT")
                || evaluation.contains("CORRECTE")
                || evaluation.contains("ACCEPT")) {
            return true;
        }

        return estimatesAreEqual(request);
    }

    /*
     * Compare les estimations prédites avec les estimations finales.
     */
    private boolean estimatesAreEqual(ExpertFeedbackRequest request) {
        return equalsMoney(request.getPredictedEstimationMin(), request.getFinalEstimationMin())
                && equalsMoney(request.getPredictedEstimationMoyenne(), request.getFinalEstimationMoyenne())
                && equalsMoney(request.getPredictedEstimationMax(), request.getFinalEstimationMax());
    }

    /*
     * Compare deux montants avec une petite marge d’erreur.
     */
    private boolean equalsMoney(Double a, Double b) {
        if (a == null || b == null) {
            return false;
        }

        return Math.abs(a - b) < 0.01;
    }

    /*
     * Vérifie que l’estimation finale est complète.
     */
    private boolean hasFinalEstimate(ExpertFeedbackRequest request) {
        return request != null
                && request.getFinalEstimationMin() != null
                && request.getFinalEstimationMoyenne() != null
                && request.getFinalEstimationMax() != null;
    }

    /*
     * Normalise le type de contrat ou de sinistre validé par l’expert.
     */
    private String normalizeType(String value) {
        String v = safe(value).toUpperCase();

        if (v.contains("AUTO")) return "AUTO";
        if (v.contains("HABITATION")) return "HABITATION";
        if (v.contains("SANTE")) return "SANTE";
        if (v.contains("VOYAGE")) return "VOYAGE";
        if (v.contains("VIE")) return "VIE";
        if (v.contains("LIFE")) return "VIE";

        return "INCONNU";
    }

    /*
     * Normalise la décision finale de validation.
     */
    private String normalizeDecision(String value) {
        String v = safe(value).toUpperCase();

        if (v.contains("COUVERT")) return "COUVERT";
        if (v.contains("EXCLU")) return "EXCLU";

        return "INCONNU";
    }

    /*
     * Limite la note de satisfaction entre 1 et 5.
     */
    private Integer normalizeSatisfaction(Integer value) {
        if (value == null) {
            return null;
        }

        return Math.max(1, Math.min(5, value));
    }

    /*
     * Fusionne le commentaire global avec le commentaire spécifique de l’agent.
     */
    private String mergeComments(String global, String local) {
        String g = safe(global);
        String l = safe(local);

        if (g.isBlank()) return l;
        if (l.isBlank()) return g;

        return g + " | " + l;
    }

    /*
     * Vérifie si une chaîne contient du texte.
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /*
     * Sécurise une chaîne null et supprime les espaces inutiles.
     */
    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /*
     * Formate un nombre décimal avec deux chiffres après la virgule.
     */
    private String safeDouble(Double value) {
        return value == null
                ? "N/A"
                : String.format(java.util.Locale.US, "%.2f", value);
    }
}