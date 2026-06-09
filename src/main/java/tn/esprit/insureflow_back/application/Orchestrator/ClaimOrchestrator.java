package tn.esprit.insureflow_back.application.Orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.application.service.ClaimPdfExtractorApplicationService;
import tn.esprit.insureflow_back.application.service.RapportClientService;
import tn.esprit.insureflow_back.application.service.RapportService;
import tn.esprit.insureflow_back.domain.enums.ClaimStatus;
import tn.esprit.insureflow_back.domain.model.AgentResult;
import tn.esprit.insureflow_back.domain.model.Claim;
import tn.esprit.insureflow_back.domain.port.out.AgentResultRepositoryPort;
import tn.esprit.insureflow_back.domain.port.out.ClaimRepositoryPort;
import tn.esprit.insureflow_back.infrastructure.adapter.out.ai.AgentEstimateur;
import tn.esprit.insureflow_back.infrastructure.adapter.out.ai.AgentFraude;
import tn.esprit.insureflow_back.infrastructure.adapter.out.ai.AgentRouteur;
import tn.esprit.insureflow_back.infrastructure.adapter.out.ai.AgentValidateur;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimOrchestrator {

    private final AgentRouteur agentRouteur;
    private final AgentValidateur agentValidateur;
    private final AgentEstimateur agentEstimateur;
    private final AgentResultRepositoryPort agentResultRepositoryPort;
    private final ClaimRepositoryPort claimRepositoryPort;

    private final ClaimPdfExtractorApplicationService pdfExtractorService;
    private final RapportService rapportService;
    private final RapportClientService rapportClientService;

    @Async("agentExecutor")
    public void processClaim(Claim claim) {
        Instant totalStart = Instant.now();

        if (claim == null || claim.getId() == null) {
            log.error("Orchestrator - claim null ou id null");
            return;
        }

        log.info("Orchestrator - démarrage traitement claim #{}", claim.getId());

        Claim freshClaim = claimRepositoryPort.findByIdWithDocuments(claim.getId())
                .orElseThrow(() -> new RuntimeException("Claim introuvable : " + claim.getId()));

        updateStatus(freshClaim, ClaimStatus.IN_ANALYSIS);

        AgentResult routeResult = null;
        AgentResult validationResult = null;
        AgentResult estimationResult = null;
        AgentResult fraudResult = null;

        try {
            /*
             * Étape 1 : classification du sinistre.
             */
            routeResult = safeRunRouteur(freshClaim);
            routeResult.setClaim(freshClaim);
            agentResultRepositoryPort.save(routeResult);

            String routedType = extractRoutedType(routeResult);

            if (routeResult.isNeedsHumanReview()) {
                finalizeClaim(
                        freshClaim,
                        ClaimStatus.PENDING_VALIDATION,
                        routeResult,
                        null,
                        null,
                        null,
                        totalStart
                );
                return;
            }

            /*
             * Étape 2 : extraction du contenu documentaire.
             */
            String claimPdfText = safeExtractPdfText(freshClaim);

            if (claimPdfText == null || claimPdfText.isBlank()) {
                claimPdfText = safeText(freshClaim.getDescription());
            }

            /*
             * Étape 3 : validation de la couverture.
             */
            validationResult = safeRunValidateur(
                    freshClaim,
                    claimPdfText,
                    routedType
            );

            validationResult.setClaim(freshClaim);
            agentResultRepositoryPort.save(validationResult);

            String validationDecision = extractValidationDecision(validationResult);

            if ("EXCLU".equals(validationDecision)) {
                finalizeClaim(
                        freshClaim,
                        ClaimStatus.REJECTED,
                        routeResult,
                        validationResult,
                        null,
                        null,
                        totalStart
                );
                return;
            }

            if ("INCONNU".equals(validationDecision)
                    || validationResult.isNeedsHumanReview()) {
                finalizeClaim(
                        freshClaim,
                        ClaimStatus.PENDING_VALIDATION,
                        routeResult,
                        validationResult,
                        null,
                        null,
                        totalStart
                );
                return;
            }

            /*
             * Étape 4 : estimation financière.
             */
            estimationResult = safeRunEstimateur(
                    freshClaim,
                    routeResult,
                    validationResult
            );

            estimationResult.setClaim(freshClaim);
            agentResultRepositoryPort.save(estimationResult);

            /*
             * Étape 5 : analyse du risque de fraude.
             *
             * L'agent fraude ne doit pas accuser le client.
             * Il donne uniquement un niveau de risque et indique si une validation humaine est recommandée.
             */

            /*
             * Étape 6 : décision finale.
             *
             * Si l'estimateur ou l'agent fraude demande une revue humaine,
             * le dossier passe en attente de validation.
             */
            boolean needsHumanReview =
                    estimationResult.isNeedsHumanReview()
                            || fraudResult.isNeedsHumanReview();

            if (needsHumanReview) {
                finalizeClaim(
                        freshClaim,
                        ClaimStatus.PENDING_VALIDATION,
                        routeResult,
                        validationResult,
                        estimationResult,
                        fraudResult,
                        totalStart
                );
            } else {
                finalizeClaim(
                        freshClaim,
                        ClaimStatus.APPROVED,
                        routeResult,
                        validationResult,
                        estimationResult,
                        fraudResult,
                        totalStart
                );
            }

        } catch (Exception e) {
            log.error(
                    "Erreur orchestrateur claim #{} : {}",
                    freshClaim.getId(),
                    e.getMessage(),
                    e
            );

            updateStatus(freshClaim, ClaimStatus.PENDING_VALIDATION);
            freshClaim.setClientReport(null);

            try {
                generateAndSaveReports(
                        freshClaim,
                        routeResult,
                        validationResult,
                        estimationResult,
                        fraudResult
                );
            } catch (Exception reportError) {
                log.error(
                        "Erreur sauvegarde rapports après échec claim #{} : {}",
                        freshClaim.getId(),
                        reportError.getMessage(),
                        reportError
                );
            }

            logDuration("Workflow terminé en erreur", totalStart);
        }
    }

    private String safeExtractPdfText(Claim claim) {
        Instant start = Instant.now();

        try {
            String text = pdfExtractorService.extractTextFromClaim(claim);
            logDuration("Extraction PDF terminée", start);
            return text;

        } catch (Exception e) {
            log.error(
                    "Erreur extraction PDF claim #{} : {}",
                    claim.getId(),
                    e.getMessage(),
                    e
            );

            return safeText(claim.getDescription());
        }
    }

    private AgentResult safeRunRouteur(Claim claim) {
        Instant start = Instant.now();

        try {
            AgentResult result = agentRouteur.classifier(claim);
            logDuration("AgentRouteur terminé", start);
            return result;

        } catch (Exception e) {
            log.error(
                    "Erreur AgentRouteur claim #{} : {}",
                    claim.getId(),
                    e.getMessage(),
                    e
            );

            return buildFallbackAgentResult(
                    "AgentRouteur",
                    "Type de sinistre classifié : INCONNU | Justification : erreur routeur",
                    0.0,
                    true,
                    claim,
                    "{\"type\":\"INCONNU\",\"confidence\":0.0,\"justification\":\"erreur routeur\"}"
            );
        }
    }

    private AgentResult safeRunValidateur(
            Claim claim,
            String claimPdfText,
            String routedType
    ) {
        Instant start = Instant.now();

        try {
            AgentResult result = agentValidateur.validate(
                    claim,
                    claimPdfText,
                    routedType
            );

            logDuration("AgentValidateur terminé", start);
            return result;

        } catch (Exception e) {
            log.error(
                    "Erreur AgentValidateur claim #{} : {}",
                    claim.getId(),
                    e.getMessage(),
                    e
            );

            return buildFallbackAgentResult(
                    "AgentValidateur",
                    "INCONNU",
                    0.0,
                    true,
                    claim,
                    "{\"decision\":\"INCONNU\",\"confidence\":0.0,\"justification\":\"erreur validateur\",\"needsHumanReview\":true}"
            );
        }
    }

    private AgentResult safeRunEstimateur(
            Claim claim,
            AgentResult routeResult,
            AgentResult validationResult
    ) {
        Instant start = Instant.now();

        try {
            AgentResult result = agentEstimateur.estimate(
                    claim,
                    routeResult,
                    validationResult
            );

            logDuration("AgentEstimateur terminé", start);
            return result;

        } catch (Exception e) {
            log.error(
                    "Erreur AgentEstimateur claim #{} : {}",
                    claim.getId(),
                    e.getMessage(),
                    e
            );

            return buildFallbackAgentResult(
                    "AgentEstimateur",
                    "Estimation min: 0.00 DT | moyenne: 0.00 DT | max: 0.00 DT",
                    0.0,
                    true,
                    claim,
                    "{\"estimationMin\":0.0,\"estimationMax\":0.0,\"estimationMoyenne\":0.0,\"confidence\":0.0,\"analyse\":\"erreur estimateur\",\"needsHumanReview\":true}"
            );
        }
    }



    private void finalizeClaim(
            Claim claim,
            ClaimStatus finalStatus,
            AgentResult routeResult,
            AgentResult validationResult,
            AgentResult estimationResult,
            AgentResult fraudResult,
            Instant totalStart
    ) {
        updateStatus(claim, finalStatus);

        generateAndSaveReports(
                claim,
                routeResult,
                validationResult,
                estimationResult,
                fraudResult
        );

        log.info(
                "Workflow terminé - claim #{} statut final : {}",
                claim.getId(),
                claim.getStatus()
        );

        logDuration("Temps total workflow", totalStart);
    }

    private void generateAndSaveReports(
            Claim claim,
            AgentResult routeResult,
            AgentResult validationResult,
            AgentResult estimationResult,
            AgentResult fraudResult
    ) {
        try {
            String expertReport = rapportService.genererRapport(
                    claim,
                    routeResult,
                    validationResult,
                    estimationResult
            );

            expertReport = appendFraudSectionToExpertReport(expertReport, fraudResult);

            claim.setAiReport(expertReport);

            if (ClaimStatus.PENDING_VALIDATION.equals(claim.getStatus())) {
                claim.setClientReport(null);

            } else if (ClaimStatus.APPROVED.equals(claim.getStatus())
                    || ClaimStatus.REJECTED.equals(claim.getStatus())) {

                String clientReport = rapportClientService.genererRapportClient(
                        claim,
                        routeResult,
                        validationResult,
                        estimationResult
                );

                claim.setClientReport(clientReport);

            } else {
                claim.setClientReport(null);
            }

            claimRepositoryPort.save(claim);

            log.info("Rapports sauvegardés - claim #{}", claim.getId());

        } catch (Exception e) {
            log.error(
                    "Erreur génération rapports claim #{} : {}",
                    claim.getId(),
                    e.getMessage(),
                    e
            );
        }
    }

    private String appendFraudSectionToExpertReport(
            String expertReport,
            AgentResult fraudResult
    ) {
        if (fraudResult == null) {
            return expertReport;
        }

        StringBuilder builder = new StringBuilder();

        if (expertReport != null && !expertReport.isBlank()) {
            builder.append(expertReport.trim());
        }

        builder.append("\n\n")
                .append("=== Analyse du risque de fraude ===")
                .append("\n")
                .append("Conclusion : ")
                .append(safeText(fraudResult.getConclusion()))
                .append("\n")
                .append("Score : ")
                .append(fraudResult.getConfidenceScore())
                .append("\n")
                .append("Validation humaine requise : ")
                .append(fraudResult.isNeedsHumanReview() ? "oui" : "non");

        if (fraudResult.getRawLlmResponse() != null
                && !fraudResult.getRawLlmResponse().isBlank()) {
            builder.append("\n")
                    .append("Réponse brute IA : ")
                    .append(fraudResult.getRawLlmResponse());
        }

        return builder.toString();
    }

    private String extractRoutedType(AgentResult routeResult) {
        if (routeResult == null) {
            return "INCONNU";
        }

        String value = (
                safeText(routeResult.getConclusion()) + " " +
                        safeText(routeResult.getRawLlmResponse())
        ).toUpperCase(Locale.ROOT);

        if (value.contains("AUTO")) return "AUTO";
        if (value.contains("SANTE") || value.contains("SANTÉ")) return "SANTE";
        if (value.contains("HABITATION")) return "HABITATION";
        if (value.contains("VOYAGE")) return "VOYAGE";
        if (value.contains("VIE")) return "VIE";

        return "INCONNU";
    }

    private String extractValidationDecision(AgentResult validationResult) {
        if (validationResult == null) {
            return "INCONNU";
        }

        String value = (
                safeText(validationResult.getConclusion()) + " " +
                        safeText(validationResult.getRawLlmResponse())
        ).toUpperCase(Locale.ROOT);

        if (value.contains("\"DECISION\":\"EXCLU\"")
                || value.contains("\"DECISION\": \"EXCLU\"")
                || value.contains("DECISION : EXCLU")
                || value.equals("EXCLU")) {
            return "EXCLU";
        }

        if (value.contains("\"DECISION\":\"INCONNU\"")
                || value.contains("\"DECISION\": \"INCONNU\"")
                || value.contains("DECISION : INCONNU")
                || value.equals("INCONNU")
                || value.contains("INCERTAIN")
                || value.contains("À VÉRIFIER")
                || value.contains("A VERIFIER")) {
            return "INCONNU";
        }

        if (value.contains("\"DECISION\":\"COUVERT\"")
                || value.contains("\"DECISION\": \"COUVERT\"")
                || value.contains("DECISION : COUVERT")
                || value.equals("COUVERT")) {
            return "COUVERT";
        }

        if (validationResult.isNeedsHumanReview()) {
            return "INCONNU";
        }

        return "INCONNU";
    }

    private void updateStatus(Claim claim, ClaimStatus status) {
        claim.setStatus(status);
        claimRepositoryPort.save(claim);

        log.info("Statut claim #{} -> {}", claim.getId(), status);
    }

    private void logDuration(String label, Instant start) {
        long ms = Duration.between(start, Instant.now()).toMillis();
        log.info("{} en {} ms", label, ms);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private AgentResult buildFallbackAgentResult(
            String agentName,
            String conclusion,
            double confidenceScore,
            boolean needsHumanReview,
            Claim claim,
            String rawLlmResponse
    ) {
        AgentResult result = new AgentResult();

        result.setAgentName(agentName);
        result.setConclusion(conclusion);
        result.setConfidenceScore(confidenceScore);
        result.setNeedsHumanReview(needsHumanReview);
        result.setClaim(claim);
        result.setRawLlmResponse(rawLlmResponse);
        result.setCreatedAt(LocalDateTime.now());

        return result;
    }
}