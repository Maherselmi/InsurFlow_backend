package tn.esprit.insureflow_back.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.insureflow_back.application.Orchestrator.ClaimOrchestrator;
import tn.esprit.insureflow_back.application.dto.AssistantRequest;
import tn.esprit.insureflow_back.application.dto.AssistantResponse;
import tn.esprit.insureflow_back.application.dto.ClaimConversationDraft;
import tn.esprit.insureflow_back.application.dto.DraftDocument;
import tn.esprit.insureflow_back.domain.enums.ClaimConversationStep;
import tn.esprit.insureflow_back.domain.model.AgentResult;
import tn.esprit.insureflow_back.domain.model.Claim;
import tn.esprit.insureflow_back.domain.model.Policy;
import tn.esprit.insureflow_back.domain.port.in.ClaimUseCase;
import tn.esprit.insureflow_back.domain.port.in.PolicyUseCase;
import tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository.AgentResultRepository;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AssistantApplicationService {

    private final ChatClient assistantChatClient;
    private final PolicyUseCase policyUseCase;
    private final ClaimUseCase claimUseCase;
    private final ClaimOrchestrator claimOrchestrator;
    private final AssistantDatasetCsvService assistantDatasetCsvService;
    private final AgentResultRepository agentResultRepository;
    private final ObjectMapper objectMapper;

    private final Map<String, ClaimConversationDraft> declarationDrafts =
            new ConcurrentHashMap<>();

    public AssistantResponse ask(AssistantRequest request) {
        String userMessage = request.getMessage() != null
                ? request.getMessage().trim()
                : "";

        String normalizedMessage = normalizeText(userMessage);
        Long clientId = request.getClientId();

        if (clientId == null) {
            return new AssistantResponse(
                    assistantDatasetCsvService.getMessage(
                            "NOT_LOGGED_IN",
                            "Veuillez vous connecter pour utiliser l’assistant InsurFlow."
                    )
            );
        }

        String draftKey = buildDraftKey(clientId, request.getConversationId());
        ClaimConversationDraft existingDraft = declarationDrafts.get(draftKey);

        if (existingDraft != null && existingDraft.getStep() != ClaimConversationStep.NONE) {
            return continueClaimDeclaration(draftKey, clientId, userMessage, existingDraft);
        }

        if (isStartClaimDeclaration(normalizedMessage)) {
            String detectedType = extractRequestedType(normalizedMessage);
            return startClaimDeclaration(draftKey, clientId, detectedType);
        }

        if (isClaimRequest(normalizedMessage)) {
            String requestedType = extractRequestedType(normalizedMessage);
            String requestedStatus = extractRequestedStatus(normalizedMessage);
            return getClientClaimsResponse(clientId, requestedType, requestedStatus);
        }

        if (isPolicyRequest(normalizedMessage)) {
            String requestedType = extractRequestedType(normalizedMessage);
            return getClientPoliciesResponse(clientId, requestedType);
        }

        return generalInsuranceAnswer(userMessage);
    }

    public AssistantResponse uploadClaimDocuments(
            Long clientId,
            String conversationId,
            List<MultipartFile> documents
    ) {
        if (clientId == null) {
            return new AssistantResponse(
                    assistantDatasetCsvService.getMessage(
                            "NOT_LOGGED_IN_UPLOAD",
                            "Veuillez vous connecter pour joindre les documents."
                    )
            );
        }

        String draftKey = buildDraftKey(clientId, conversationId);
        ClaimConversationDraft draft = declarationDrafts.get(draftKey);

        if (draft == null || draft.getStep() != ClaimConversationStep.DOCUMENTS) {
            return new AssistantResponse(
                    assistantDatasetCsvService.getMessage(
                            "NO_PENDING_DOCUMENTS",
                            "Aucune déclaration de sinistre n’est actuellement en attente de documents."
                    )
            );
        }

        if (documents == null || documents.isEmpty()) {
            return new AssistantResponse(
                    assistantDatasetCsvService.getMessage(
                            "NO_DOCUMENT_RECEIVED",
                            "Aucun document reçu. Veuillez joindre au moins un fichier ou écrire : continuer sans document."
                    ),
                    true,
                    true
            );
        }

        try {
            for (MultipartFile file : documents) {
                if (file != null && !file.isEmpty()) {
                    draft.getDocuments().add(
                            new DraftDocument(
                                    file.getOriginalFilename(),
                                    file.getContentType(),
                                    file.getBytes()
                            )
                    );
                }
            }

            draft.setStep(ClaimConversationStep.CONFIRMATION);
            return buildConfirmationMessage(draft);

        } catch (Exception e) {
            return new AssistantResponse(
                    assistantDatasetCsvService.getMessage(
                            "DOCUMENT_UPLOAD_ERROR",
                            "Erreur lors de la réception des documents. Veuillez réessayer."
                    ),
                    true,
                    true
            );
        }
    }

    @Transactional(readOnly = true)
    public AssistantResponse getClaimAgentResults(Long claimId) {
        if (claimId == null) {
            return new AssistantResponse("Identifiant du sinistre invalide.");
        }

        List<AgentResult> results =
                agentResultRepository.findByClaimIdOrderByCreatedAtAsc(claimId);

        boolean routeurDone = hasAgent(results, "routeur");
        boolean validateurDone = hasAgent(results, "validateur");
        boolean estimateurDone = hasAgent(results, "estimateur");

        boolean processing = !(routeurDone && validateurDone && estimateurDone);

        AssistantResponse response = new AssistantResponse();

        response.setClaimId(claimId);
        response.setProcessing(processing);
        response.setDeclarationCompleted(!processing);
        response.setNeedsFileUpload(false);
        response.setAnswer(buildAgentConversationAnswer(claimId, results, processing));

        return response;
    }

    private AssistantResponse continueClaimDeclaration(
            String draftKey,
            Long clientId,
            String message,
            ClaimConversationDraft draft
    ) {
        String normalizedMessage = normalizeText(message);

        if (normalizedMessage.equals("annuler")
                || normalizedMessage.equals("cancel")
                || normalizedMessage.equals("stop")) {

            declarationDrafts.remove(draftKey);
            return new AssistantResponse(
                    assistantDatasetCsvService.getMessage(
                            "DECLARATION_CANCELLED",
                            "D’accord, la déclaration du sinistre a été annulée."
                    )
            );
        }

        return switch (draft.getStep()) {
            case CHOOSE_CLAIM_TYPE -> handleClaimTypeChoice(clientId, message, draft);
            case CHOOSE_POLICY -> handlePolicyChoice(clientId, message, draft);
            case INCIDENT_DATE -> handleIncidentDate(message, draft);
            case DESCRIPTION -> handleDescription(message, draft);
            case DOCUMENTS -> handleDocumentsStep(message, draft);
            case CONFIRMATION -> handleConfirmation(draftKey, clientId, message, draft);
            default -> {
                declarationDrafts.remove(draftKey);
                yield new AssistantResponse(
                        assistantDatasetCsvService.getMessage(
                                "RESET_DECLARATION",
                                "La déclaration a été réinitialisée. Vous pouvez recommencer en écrivant : je veux déclarer un sinistre."
                        )
                );
            }
        };
    }

    private AssistantResponse handleConfirmation(
            String draftKey,
            Long clientId,
            String message,
            ClaimConversationDraft draft
    ) {
        String normalizedMessage = normalizeText(message);

        if (isNegativeConfirmation(normalizedMessage)) {
            declarationDrafts.remove(draftKey);
            return new AssistantResponse(
                    assistantDatasetCsvService.getMessage(
                            "DECLARATION_CANCELLED",
                            "D’accord, la déclaration du sinistre a été annulée."
                    )
            );
        }

        if (!isPositiveConfirmation(normalizedMessage)) {
            return new AssistantResponse(
                    assistantDatasetCsvService.getMessage(
                            "CONFIRMATION_REQUIRED",
                            "Veuillez répondre par OUI pour valider la déclaration ou NON pour l’annuler."
                    ),
                    true,
                    false
            );
        }

        try {
            Claim savedClaim = claimUseCase.createClaimFromConversation(draft);

            claimOrchestrator.processClaim(savedClaim);

            declarationDrafts.remove(draftKey);

            String template = assistantDatasetCsvService.getMessage(
                    "CLAIM_DECLARED_PROCESSING",
                    """
                    Votre sinistre a été déclaré avec succès.

                    - Numéro dossier : #%s
                    - Statut initial : %s

                    Votre dossier est maintenant en cours d’analyse IA. Les résultats seront affichés automatiquement dans cette conversation.
                    """
            );

            AssistantResponse response = new AssistantResponse();

            response.setAnswer(template.formatted(savedClaim.getId(), savedClaim.getStatus()));
            response.setClaimDeclarationMode(false);
            response.setNeedsFileUpload(false);
            response.setDeclarationCompleted(true);
            response.setClaimId(savedClaim.getId());
            response.setStatus(savedClaim.getStatus().name());
            response.setProcessing(true);

            return response;

        } catch (Exception e) {
            return new AssistantResponse(
                    "Une erreur est survenue lors de la création du sinistre : " + e.getMessage()
            );
        }
    }

    private AssistantResponse handleClaimTypeChoice(
            Long clientId,
            String message,
            ClaimConversationDraft draft
    ) {
        String claimType = normalizeClaimType(message);

        if (claimType == null) {
            return new AssistantResponse(
                    assistantDatasetCsvService.getMessage(
                            "UNKNOWN_CLAIM_TYPE",
                            "Je n’ai pas compris le type de sinistre.\n\nVeuillez choisir parmi : AUTO, SANTE, HABITATION, VOYAGE ou VIE."
                    ),
                    true,
                    false
            );
        }

        List<Policy> matchingPolicies =
                policyUseCase.getPoliciesByClientId(clientId)
                        .stream()
                        .filter(this::isPolicyActive)
                        .filter(policy -> isSamePolicyType(policy.getType(), claimType))
                        .toList();

        if (matchingPolicies.isEmpty()) {
            return new AssistantResponse(
                    "Je ne trouve aucune police active de type " + claimType +
                            " associée à votre compte.",
                    true,
                    false
            );
        }

        draft.setClaimType(claimType);
        draft.setStep(ClaimConversationStep.CHOOSE_POLICY);

        StringBuilder response = new StringBuilder();

        response.append("Très bien. Vous avez choisi un sinistre de type ")
                .append(claimType)
                .append(".\n\n");

        response.append("Voici vos polices actives correspondant à ce type :\n\n");

        for (Policy policy : matchingPolicies) {
            response.append("- ID ")
                    .append(policy.getId())
                    .append(" : ")
                    .append(policy.getPolicyNumber())
                    .append(" — ")
                    .append(policy.getType())
                    .append("\n");
        }

        response.append("\nQuelle police concerne ce sinistre ? Répondez avec l’ID de la police.");

        return new AssistantResponse(response.toString(), true, false);
    }

    private AssistantResponse handlePolicyChoice(
            Long clientId,
            String message,
            ClaimConversationDraft draft
    ) {
        try {
            Long policyId = Long.parseLong(message.trim());

            Policy policy = policyUseCase.getPolicyById(policyId);

            if (policy.getClient() == null || !policy.getClient().getId().equals(clientId)) {
                return new AssistantResponse(
                        "Cette police ne semble pas appartenir à votre compte.",
                        true,
                        false
                );
            }

            if (!isPolicyActive(policy)) {
                return new AssistantResponse(
                        "Cette police n’est pas active.",
                        true,
                        false
                );
            }

            if (!isSamePolicyType(policy.getType(), draft.getClaimType())) {
                return new AssistantResponse(
                        "Cette police ne correspond pas au type de sinistre choisi.",
                        true,
                        false
                );
            }

            draft.setPolicyId(policy.getId());
            draft.setPolicyNumber(policy.getPolicyNumber());
            draft.setPolicyType(policy.getType());
            draft.setStep(ClaimConversationStep.INCIDENT_DATE);

            return new AssistantResponse(
                    assistantDatasetCsvService.getMessage(
                            "ASK_INCIDENT_DATE",
                            "Parfait. Quelle est la date de l’incident ?\n\nFormat : AAAA-MM-JJ"
                    ),
                    true,
                    false
            );

        } catch (Exception e) {
            return new AssistantResponse(
                    "Veuillez envoyer uniquement l’ID valide de la police.",
                    true,
                    false
            );
        }
    }

    private AssistantResponse handleIncidentDate(String message, ClaimConversationDraft draft) {
        try {
            LocalDate incidentDate = LocalDate.parse(message.trim());

            if (incidentDate.isAfter(LocalDate.now())) {
                return new AssistantResponse(
                        assistantDatasetCsvService.getMessage(
                                "FUTURE_INCIDENT_DATE",
                                "La date de l’incident ne peut pas être dans le futur."
                        ),
                        true,
                        false
                );
            }

            draft.setIncidentDate(incidentDate);
            draft.setStep(ClaimConversationStep.DESCRIPTION);

            return new AssistantResponse(
                    assistantDatasetCsvService.getMessage(
                            "ASK_DESCRIPTION",
                            "Merci. Maintenant, décrivez en détail ce qui s’est passé."
                    ),
                    true,
                    false
            );

        } catch (Exception e) {
            return new AssistantResponse(
                    assistantDatasetCsvService.getMessage(
                            "INVALID_INCIDENT_DATE",
                            "Format de date invalide. Exemple : 2026-05-06"
                    ),
                    true,
                    false
            );
        }
    }

    private AssistantResponse handleDescription(String message, ClaimConversationDraft draft) {
        if (message == null || message.trim().length() < 20) {
            return new AssistantResponse(
                    assistantDatasetCsvService.getMessage(
                            "DESCRIPTION_TOO_SHORT",
                            "Veuillez donner une description plus détaillée du sinistre."
                    ),
                    true,
                    false
            );
        }

        draft.setDescription(message.trim());
        draft.setStep(ClaimConversationStep.DOCUMENTS);

        return new AssistantResponse(
                assistantDatasetCsvService.getMessage(
                        "ASK_DOCUMENTS",
                        "Merci. Veuillez maintenant joindre les documents disponibles.\nSi vous n’avez aucun document, écrivez : continuer sans document."
                ),
                true,
                true
        );
    }

    private AssistantResponse handleDocumentsStep(String message, ClaimConversationDraft draft) {
        if (assistantDatasetCsvService.matchesIntent("CONTINUE_WITHOUT_DOCUMENT", message)) {
            draft.setStep(ClaimConversationStep.CONFIRMATION);
            return buildConfirmationMessage(draft);
        }

        return new AssistantResponse(
                assistantDatasetCsvService.getMessage(
                        "DOCUMENTS_STEP_HELP",
                        "Veuillez joindre les documents avec le bouton de pièce jointe, ou écrivez : continuer sans document."
                ),
                true,
                true
        );
    }

    private AssistantResponse buildConfirmationMessage(ClaimConversationDraft draft) {
        String response = """
                Voici le résumé de votre déclaration :

                - Type de sinistre : %s
                - Police : %s — %s
                - Date de l’incident : %s
                - Description : %s
                - Documents joints : %s

                Voulez-vous valider cette déclaration ?
                Répondez par OUI pour valider ou NON pour annuler.
                """.formatted(
                draft.getClaimType(),
                draft.getPolicyNumber(),
                draft.getPolicyType(),
                draft.getIncidentDate(),
                draft.getDescription(),
                draft.getDocuments() != null ? draft.getDocuments().size() : 0
        );

        return new AssistantResponse(response, true, false);
    }

    private AssistantResponse startClaimDeclaration(String draftKey, Long clientId, String detectedType) {
        ClaimConversationDraft draft = new ClaimConversationDraft();

        draft.setClientId(clientId);

        declarationDrafts.put(draftKey, draft);

        if (detectedType != null) {
            draft.setStep(ClaimConversationStep.CHOOSE_CLAIM_TYPE);
            return handleClaimTypeChoice(clientId, detectedType, draft);
        }

        draft.setStep(ClaimConversationStep.CHOOSE_CLAIM_TYPE);

        String response = assistantDatasetCsvService.getMessage(
                "CHOOSE_CLAIM_TYPE",
                """
                Très bien, je vais vous aider à déclarer un sinistre.

                Quel type de sinistre voulez-vous déclarer ?

                - AUTO
                - SANTE
                - HABITATION
                - VOYAGE
                - VIE
                """
        );

        return new AssistantResponse(response, true, false);
    }

    private AssistantResponse getClientPoliciesResponse(Long clientId, String requestedType) {
        List<Policy> policies = policyUseCase.getPoliciesByClientId(clientId)
                .stream()
                .filter(this::isPolicyActive)
                .filter(policy -> requestedType == null || isSamePolicyType(policy.getType(), requestedType))
                .toList();

        if (policies.isEmpty()) {
            if (requestedType != null) {
                return new AssistantResponse(
                        "Vous n’avez aucune police active de type " + requestedType + "."
                );
            }

            return new AssistantResponse(
                    assistantDatasetCsvService.getMessage(
                            "NO_ACTIVE_POLICY",
                            "Vous n’avez aucune police d’assurance active actuellement."
                    )
            );
        }

        StringBuilder response = new StringBuilder();

        if (requestedType != null) {
            response.append("Voici vos polices actives de type ")
                    .append(requestedType)
                    .append(" :\n\n");
        } else {
            response.append("Voici vos polices d’assurance actives :\n\n");
        }

        for (Policy policy : policies) {
            response.append("- ID ")
                    .append(policy.getId())
                    .append(" : ")
                    .append(policy.getPolicyNumber())
                    .append(" — Type : ")
                    .append(policy.getType());

            if (policy.getFormule() != null) {
                response.append(" — Formule : ").append(policy.getFormule());
            }

            response.append("\n");
        }

        return new AssistantResponse(response.toString());
    }

    private AssistantResponse getClientClaimsResponse(Long clientId, String requestedType, String requestedStatus) {
        List<Claim> claims = claimUseCase.getClaimsByClientId(clientId)
                .stream()
                .filter(claim -> requestedType == null || claimMatchesType(claim, requestedType))
                .filter(claim -> requestedStatus == null || claimMatchesStatus(claim, requestedStatus))
                .toList();

        if (claims.isEmpty()) {
            StringBuilder empty = new StringBuilder("Aucun dossier de sinistre trouvé");

            if (requestedType != null) {
                empty.append(" pour le type ").append(requestedType);
            }

            if (requestedStatus != null) {
                empty.append(" avec le statut ").append(requestedStatus);
            }

            empty.append(".");

            return new AssistantResponse(empty.toString());
        }

        StringBuilder response = new StringBuilder();

        response.append("Voici vos dossiers de sinistre");

        if (requestedType != null) {
            response.append(" de type ").append(requestedType);
        }

        if (requestedStatus != null) {
            response.append(" avec le statut ").append(requestedStatus);
        }

        response.append(" :\n\n");

        for (Claim claim : claims) {
            response.append("- Dossier #")
                    .append(claim.getId())
                    .append(" — Statut : ")
                    .append(claim.getStatus());

            if (claim.getIncidentDate() != null) {
                response.append(" — Date incident : ").append(claim.getIncidentDate());
            }

            if (claim.getPolicy() != null && claim.getPolicy().getType() != null) {
                response.append(" — Type : ").append(claim.getPolicy().getType());
            }

            if (claim.getDescription() != null && !claim.getDescription().isBlank()) {
                response.append("\n  Description : ")
                        .append(limitText(claim.getDescription(), 120));
            }

            response.append("\n");
        }

        return new AssistantResponse(response.toString());
    }

    private AssistantResponse generalInsuranceAnswer(String userMessage) {
        String systemPrompt = assistantDatasetCsvService.getPrompt("GENERAL_INSURANCE_SYSTEM_PROMPT");

        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = """
                    Tu es l'assistant virtuel InSurFlow, spécialisé dans l'assurance et les sinistres.

                    Tu peux aider le client à :
                    - comprendre ses contrats,
                    - déclarer un sinistre,
                    - suivre ses dossiers,
                    - comprendre les statuts de traitement.

                    Si le client veut déclarer un sinistre, demande-lui d’écrire :
                    "Je veux déclarer un sinistre".

                    Réponds uniquement en français.
                    Réponse courte, claire et rassurante.
                    """;
        }

        String answer = assistantChatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();

        return new AssistantResponse(answer);
    }

    private String buildAgentConversationAnswer(
            Long claimId,
            List<AgentResult> results,
            boolean processing
    ) {
        if (results == null || results.isEmpty()) {
            return assistantDatasetCsvService.getMessage(
                    "AGENT_RESULTS_PENDING",
                    "Analyse de votre dossier en cours. Votre dossier est en cours de traitement."
            );
        }

        AgentResult routeurResult = findAgentResult(results, "routeur");
        AgentResult validateurResult = findAgentResult(results, "validateur");
        AgentResult estimateurResult = findAgentResult(results, "estimateur");

        StringBuilder response = new StringBuilder();

        response.append("Votre dossier #")
                .append(claimId)
                .append(" a été analysé.")
                .append("\n\n");

        if (routeurResult != null) {
            appendClientClassification(response, routeurResult);
        }

        if (validateurResult != null) {
            appendClientCoverageDecision(response, validateurResult);
        }

        if (estimateurResult != null) {
            appendClientDamageEstimation(response, estimateurResult);
        }

        if (processing) {
            response.append("\nAnalyse encore en cours...\n")
                    .append("Certaines informations peuvent encore être mises à jour.");
        } else {
            response.append("\nAnalyse terminée.\n")
                    .append("Les résultats affichés sont une aide à la décision. ")
                    .append("Un conseiller peut vérifier le dossier si nécessaire.");
        }

        return response.toString();
    }

    private AgentResult findAgentResult(List<AgentResult> results, String keyword) {
        if (results == null || keyword == null) {
            return null;
        }

        String normalizedKeyword = normalizeText(keyword);

        return results.stream()
                .filter(result -> normalizeText(result.getAgentName()).contains(normalizedKeyword))
                .findFirst()
                .orElse(null);
    }

    private void appendClientClassification(StringBuilder response, AgentResult result) {
        JsonNode json = readAgentJson(result);

        String type = firstText(
                json,
                "type",
                "claimType",
                "classification",
                "detectedType"
        );

        if (type.isBlank()) {
            type = extractTypeFromConclusion(result.getConclusion());
        }

        if (!type.isBlank()) {
            response.append("Type de sinistre détecté : ")
                    .append(type.toUpperCase(Locale.ROOT))
                    .append("\n\n");
        }
    }

    private void appendClientCoverageDecision(StringBuilder response, AgentResult result) {
        JsonNode json = readAgentJson(result);

        String decision = firstText(
                json,
                "decision",
                "coverageDecision",
                "status",
                "result"
        );

        String justification = firstText(
                json,
                "justification",
                "reason",
                "reasoning",
                "explanation"
        );

        if (decision.isBlank()) {
            decision = safeString(result.getConclusion());
        }

        String clientDecision = toClientDecisionLabel(decision);

        response.append(clientDecision)
                .append("\n\n");

        if (!justification.isBlank()) {
            response.append("Justification :\n")
                    .append(justification)
                    .append("\n\n");
        }
    }

    private void appendClientDamageEstimation(StringBuilder response, AgentResult result) {
        JsonNode json = readAgentJson(result);

        String imageAnalysis = firstText(
                json,
                "imageAnalysis",
                "image_analysis",
                "analyseImage",
                "analyse_image",
                "visualAnalysis"
        );

        String elementsEndommages = firstText(
                json,
                "elementsEndommages",
                "élémentsEndommagés",
                "visibleDamages",
                "damages",
                "damageIndicators"
        );

        String severity = firstText(
                json,
                "severity",
                "gravite",
                "gravité",
                "damageSeverity"
        );

        String estimationMin = firstText(
                json,
                "estimationMin",
                "estimation_min",
                "min",
                "minimum"
        );

        String estimationMoyenne = firstText(
                json,
                "estimationMoyenne",
                "estimation_moyenne",
                "moyenne",
                "average",
                "estimatedAverage"
        );

        String estimationMax = firstText(
                json,
                "estimationMax",
                "estimation_max",
                "max",
                "maximum"
        );

        String justification = firstText(
                json,
                "justification",
                "amountJustification",
                "estimationJustification",
                "reason",
                "explication"
        );

        String costBreakdown = firstText(
                json,
                "costBreakdown",
                "cost_breakdown",
                "detailCout",
                "detail_cout",
                "postesCout",
                "breakdown"
        );

        response.append("Analyse visuelle des dommages :\n");

        if (!imageAnalysis.isBlank()) {
            response.append(imageAnalysis).append("\n\n");
        } else {
            response.append("Analyse visuelle non détaillée par le modèle.").append("\n\n");
        }

        if (!elementsEndommages.isBlank()) {
            response.append("Dommages visibles :\n")
                    .append(elementsEndommages)
                    .append("\n\n");
        }

        if (!severity.isBlank()) {
            response.append("Gravité estimée :\n")
                    .append(toClientSeverityLabel(severity))
                    .append("\n\n");
        }

        if (!costBreakdown.isBlank()) {
            response.append("Travaux ou frais probables :\n")
                    .append(costBreakdown)
                    .append("\n\n");
        }

        appendClientEstimatedAmount(
                response,
                estimationMin,
                estimationMoyenne,
                estimationMax,
                result
        );

        if (!justification.isBlank()) {
            response.append("Pourquoi ce montant ?\n")
                    .append(justification)
                    .append("\n\n");
        }

        if (result.isNeedsHumanReview()) {
            response.append("Remarque :\n")
                    .append("Cette estimation doit être confirmée par un expert, ")
                    .append("car elle est basée sur les éléments visibles dans l’image.")
                    .append("\n");
        }
    }

    private void appendClientEstimatedAmount(
            StringBuilder response,
            String estimationMin,
            String estimationMoyenne,
            String estimationMax,
            AgentResult result
    ) {
        /*
         * IMPORTANT :
         * Après l'intégration XGBoost, les montants finaux corrigés sont stockés
         * dans result.getConclusion().
         *
         * Le rawLlmResponse contient encore les anciens montants proposés par le LLM
         * avant la fusion avec XGBoost. Donc, pour l'affichage client, on privilégie
         * toujours la conclusion finale sauvegardée par AgentEstimateur.
         */
        String conclusion = safeString(result.getConclusion());

        if (!conclusion.isBlank()) {
            response.append("Montant estimé :\n")
                    .append(formatConclusionAmount(conclusion))
                    .append("\n\n");
            return;
        }

        boolean hasMin = estimationMin != null && !estimationMin.isBlank();
        boolean hasAverage = estimationMoyenne != null && !estimationMoyenne.isBlank();
        boolean hasMax = estimationMax != null && !estimationMax.isBlank();

        if (hasMin || hasAverage || hasMax) {
            response.append("Montant estimé :\n");

            if (hasMin && hasMax) {
                response.append("entre ")
                        .append(formatAmount(estimationMin))
                        .append(" et ")
                        .append(formatAmount(estimationMax))
                        .append("\n");
            } else if (hasAverage) {
                response.append(formatAmount(estimationMoyenne))
                        .append("\n");
            } else if (hasMin) {
                response.append("à partir de ")
                        .append(formatAmount(estimationMin))
                        .append("\n");
            } else {
                response.append("jusqu’à ")
                        .append(formatAmount(estimationMax))
                        .append("\n");
            }

            if (hasAverage) {
                response.append("Estimation moyenne : ")
                        .append(formatAmount(estimationMoyenne))
                        .append("\n");
            }

            response.append("\n");
        }
    }

    private String formatConclusionAmount(String conclusion) {
        if (conclusion == null || conclusion.isBlank()) {
            return "";
        }

        return conclusion
                .replace("Estimation min:", "Minimum :")
                .replace("estimation min:", "Minimum :")
                .replace("moyenne:", "Moyenne :")
                .replace("Moyenne:", "Moyenne :")
                .replace("max:", "Maximum :")
                .replace("Max:", "Maximum :")
                .replace("|", "\n")
                .trim();
    }

    private String toClientDecisionLabel(String decision) {
        String normalized = normalizeText(decision);

        if (normalized.contains("couvert")
                || normalized.contains("approved")
                || normalized.contains("accepte")) {
            return "Votre sinistre est couvert par votre contrat.";
        }

        if (normalized.contains("exclu")
                || normalized.contains("non couvert")
                || normalized.contains("rejected")
                || normalized.contains("refuse")) {
            return "Votre sinistre ne semble pas être couvert par votre contrat.";
        }

        if (normalized.contains("inconnu")
                || normalized.contains("incertain")
                || normalized.contains("a verifier")) {
            return "La couverture de votre sinistre doit être vérifiée par un conseiller.";
        }

        return "La couverture de votre sinistre est en cours de vérification.";
    }

    private String toClientSeverityLabel(String severity) {
        String normalized = normalizeText(severity);

        if (normalized.contains("leger")
                || normalized.contains("light")
                || normalized.contains("minor")) {
            return "Dommages légers";
        }

        if (normalized.contains("modere")
                || normalized.contains("moyen")
                || normalized.contains("moderate")) {
            return "Dommages modérés";
        }

        if (normalized.contains("grave")
                || normalized.contains("severe")) {
            return "Dommages importants";
        }

        if (normalized.contains("total")) {
            return "Dommages très importants";
        }

        return severity;
    }

    private JsonNode readAgentJson(AgentResult result) {
        String raw = safeString(result.getRawLlmResponse());

        if (raw.isBlank()) {
            raw = safeString(result.getConclusion());
        }

        if (raw.isBlank()) {
            return null;
        }

        try {
            String jsonText = extractJsonObject(raw);

            if (jsonText.isBlank()) {
                return null;
            }

            return objectMapper.readTree(jsonText);

        } catch (Exception e) {
            return null;
        }
    }

    private String extractJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String clean = text
                .replaceAll("(?s)<think>.*?</think>", "")
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        int start = clean.indexOf("{");
        int end = clean.lastIndexOf("}");

        if (start >= 0 && end > start) {
            return clean.substring(start, end + 1);
        }

        return "";
    }

    private String firstText(JsonNode json, String... fields) {
        if (json == null || fields == null) {
            return "";
        }

        for (String field : fields) {
            JsonNode value = json.findValue(field);

            if (value == null || value.isNull()) {
                continue;
            }

            if (value.isArray()) {
                StringBuilder builder = new StringBuilder();

                for (JsonNode item : value) {
                    if (builder.length() > 0) {
                        builder.append(", ");
                    }

                    if (item.isObject()) {
                        builder.append(item.toString());
                    } else {
                        builder.append(item.asText());
                    }
                }

                return builder.toString();
            }

            if (value.isObject()) {
                return value.toString();
            }

            return value.asText("");
        }

        return "";
    }

    private String formatAmount(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String clean = value.trim();

        if (clean.toUpperCase(Locale.ROOT).contains("DT")
                || clean.toUpperCase(Locale.ROOT).contains("TND")) {
            return clean;
        }

        return clean + " DT";
    }

    private String extractTypeFromConclusion(String conclusion) {
        String value = normalizeText(conclusion);

        if (value.contains("auto")) {
            return "AUTO";
        }

        if (value.contains("habitation")) {
            return "HABITATION";
        }

        if (value.contains("sante")) {
            return "SANTE";
        }

        if (value.contains("voyage")) {
            return "VOYAGE";
        }

        if (value.contains("vie")) {
            return "VIE";
        }

        return "";
    }

    private boolean hasAgent(List<AgentResult> results, String keyword) {
        if (results == null || keyword == null) {
            return false;
        }

        String normalizedKeyword = normalizeText(keyword);

        return results.stream()
                .anyMatch(result -> normalizeText(result.getAgentName()).contains(normalizedKeyword));
    }

    private String buildDraftKey(Long clientId, String conversationId) {
        String safeConversationId =
                conversationId != null && !conversationId.isBlank()
                        ? conversationId.trim()
                        : "default";

        return clientId + ":" + safeConversationId;
    }

    private boolean isStartClaimDeclaration(String message) {
        return assistantDatasetCsvService.matchesIntent("START_CLAIM_DECLARATION", message);
    }

    private boolean isPolicyRequest(String message) {
        return assistantDatasetCsvService.matchesIntent("POLICY_REQUEST", message);
    }

    private boolean isClaimRequest(String message) {
        return assistantDatasetCsvService.matchesIntent("CLAIM_REQUEST", message);
    }

    private String extractRequestedType(String message) {
        return normalizeClaimType(message);
    }

    private String extractRequestedStatus(String message) {
        return assistantDatasetCsvService.detectStatus(message);
    }

    private String normalizeClaimType(String message) {
        return assistantDatasetCsvService.detectClaimType(message);
    }

    private boolean isSamePolicyType(String policyType, String claimType) {
        if (policyType == null || claimType == null) {
            return false;
        }

        String normalizedPolicyType = normalizeClaimType(policyType);
        return claimType.equals(normalizedPolicyType);
    }

    private boolean claimMatchesType(Claim claim, String requestedType) {
        if (claim == null || requestedType == null) {
            return false;
        }

        if (claim.getPolicy() != null && isSamePolicyType(claim.getPolicy().getType(), requestedType)) {
            return true;
        }

        return claim.getDescription() != null
                && normalizeClaimType(claim.getDescription()) != null
                && requestedType.equals(normalizeClaimType(claim.getDescription()));
    }

    private boolean claimMatchesStatus(Claim claim, String requestedStatus) {
        if (claim == null || claim.getStatus() == null || requestedStatus == null) {
            return false;
        }

        return requestedStatus.equalsIgnoreCase(claim.getStatus().name());
    }

    private boolean isPolicyActive(Policy policy) {
        if (policy == null) {
            return false;
        }

        if (policy.getEndDate() == null) {
            return true;
        }

        return !policy.getEndDate().isBefore(LocalDate.now());
    }

    private boolean isPositiveConfirmation(String message) {
        return assistantDatasetCsvService.matchesIntent("POSITIVE_CONFIRMATION", message);
    }

    private boolean isNegativeConfirmation(String message) {
        return assistantDatasetCsvService.matchesIntent("NEGATIVE_CONFIRMATION", message);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return normalized
                .toLowerCase(Locale.ROOT)
                .replace("’", "'")
                .replace("'", " ")
                .replace("-", " ")
                .replace("_", " ")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String limitText(String text, int max) {
        if (text == null) {
            return "";
        }

        String clean = text.trim();

        if (clean.length() <= max) {
            return clean;
        }

        return clean.substring(0, max) + "...";
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }
} 