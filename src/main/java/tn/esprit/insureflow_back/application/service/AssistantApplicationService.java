package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.insureflow_back.application.dto.AssistantRequest;
import tn.esprit.insureflow_back.application.dto.AssistantResponse;
import tn.esprit.insureflow_back.application.dto.ClaimConversationDraft;
import tn.esprit.insureflow_back.application.dto.DraftDocument;
import tn.esprit.insureflow_back.domain.enums.ClaimConversationStep;
import tn.esprit.insureflow_back.domain.model.Claim;
import tn.esprit.insureflow_back.domain.model.Policy;
import tn.esprit.insureflow_back.domain.port.in.ClaimUseCase;
import tn.esprit.insureflow_back.domain.port.in.PolicyUseCase;

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

    private final Map<String, ClaimConversationDraft> declarationDrafts =
            new ConcurrentHashMap<>();

    public AssistantResponse ask(AssistantRequest request) {
        String userMessage = request.getMessage() != null
                ? request.getMessage().trim()
                : "";

        String normalizedMessage = userMessage.toLowerCase(Locale.ROOT);
        Long clientId = request.getClientId();

        if (clientId == null) {
            return new AssistantResponse(
                    "Veuillez vous connecter pour utiliser l’assistant InsurFlow."
            );
        }

        String draftKey = buildDraftKey(clientId, request.getConversationId());
        ClaimConversationDraft existingDraft = declarationDrafts.get(draftKey);

        if (existingDraft != null
                && existingDraft.getStep() != ClaimConversationStep.NONE) {
            return continueClaimDeclaration(
                    draftKey,
                    clientId,
                    userMessage,
                    existingDraft
            );
        }

        if (isStartClaimDeclaration(normalizedMessage)) {
            return startClaimDeclaration(draftKey, clientId);
        }

        if (isPolicyRequest(normalizedMessage)) {
            return getClientPoliciesResponse(clientId);
        }

        if (isClaimRequest(normalizedMessage)) {
            return getClientClaimsResponse(clientId);
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
                    "Veuillez vous connecter pour joindre les documents."
            );
        }

        String draftKey = buildDraftKey(clientId, conversationId);
        ClaimConversationDraft draft = declarationDrafts.get(draftKey);

        if (draft == null || draft.getStep() != ClaimConversationStep.DOCUMENTS) {
            return new AssistantResponse(
                    "Aucune déclaration de sinistre n’est actuellement en attente de documents."
            );
        }

        if (documents == null || documents.isEmpty()) {
            return new AssistantResponse(
                    "Aucun document reçu. Veuillez joindre au moins un fichier ou écrire : continuer sans document.",
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
                    "Erreur lors de la réception des documents. Veuillez réessayer.",
                    true,
                    true
            );
        }
    }

    private AssistantResponse handleConfirmation(
            String draftKey,
            Long clientId,
            String message,
            ClaimConversationDraft draft
    ) {
        String normalizedMessage = message.toLowerCase(Locale.ROOT).trim();

        if (normalizedMessage.equals("non")
                || normalizedMessage.equals("annuler")) {

            declarationDrafts.remove(draftKey);

            return new AssistantResponse(
                    "D’accord, la déclaration a été annulée."
            );
        }

        if (!normalizedMessage.equals("oui")) {
            return new AssistantResponse(
                    "Veuillez répondre par OUI pour valider la déclaration ou NON pour l’annuler.",
                    true,
                    false
            );
        }

        try {
            Claim savedClaim = claimUseCase.createClaimFromConversation(draft);

            declarationDrafts.remove(draftKey);

            AssistantResponse response = new AssistantResponse();

            response.setAnswer(
                    "Votre sinistre a été déclaré avec succès.\n\n" +
                            "- Numéro dossier : #" + savedClaim.getId() + "\n" +
                            "- Statut initial : " + savedClaim.getStatus() + "\n\n" +
                            "Votre dossier est maintenant en cours de traitement."
            );

            response.setClaimDeclarationMode(false);
            response.setNeedsFileUpload(false);
            response.setDeclarationCompleted(true);
            response.setClaimId(savedClaim.getId());
            response.setStatus(savedClaim.getStatus().name());

            return response;

        } catch (Exception e) {
            return new AssistantResponse(
                    "Une erreur est survenue lors de la création du sinistre : "
                            + e.getMessage()
            );
        }
    }

    private AssistantResponse continueClaimDeclaration(
            String draftKey,
            Long clientId,
            String message,
            ClaimConversationDraft draft
    ) {
        String normalizedMessage = message.toLowerCase(Locale.ROOT).trim();

        if (normalizedMessage.equals("annuler")
                || normalizedMessage.equals("cancel")) {

            declarationDrafts.remove(draftKey);

            return new AssistantResponse(
                    "D’accord, la déclaration du sinistre a été annulée."
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
                        "La déclaration a été réinitialisée. Vous pouvez recommencer en écrivant : je veux déclarer un sinistre."
                );
            }
        };
    }

    private AssistantResponse handleClaimTypeChoice(
            Long clientId,
            String message,
            ClaimConversationDraft draft
    ) {
        String claimType = normalizeClaimType(message);

        if (claimType == null) {
            return new AssistantResponse(
                    "Je n’ai pas compris le type de sinistre.\n\n" +
                            "Veuillez choisir parmi : AUTO, SANTE, HABITATION, VOYAGE ou VIE.",
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

            if (policy.getClient() == null
                    || !policy.getClient().getId().equals(clientId)) {
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
                    "Parfait. Quelle est la date de l’incident ?\n\n" +
                            "Format : AAAA-MM-JJ",
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

    private AssistantResponse handleIncidentDate(
            String message,
            ClaimConversationDraft draft
    ) {
        try {
            LocalDate incidentDate = LocalDate.parse(message.trim());

            if (incidentDate.isAfter(LocalDate.now())) {
                return new AssistantResponse(
                        "La date de l’incident ne peut pas être dans le futur.",
                        true,
                        false
                );
            }

            draft.setIncidentDate(incidentDate);
            draft.setStep(ClaimConversationStep.DESCRIPTION);

            return new AssistantResponse(
                    "Merci. Maintenant, décrivez en détail ce qui s’est passé.",
                    true,
                    false
            );

        } catch (Exception e) {
            return new AssistantResponse(
                    "Format de date invalide. Exemple : 2026-05-06",
                    true,
                    false
            );
        }
    }

    private AssistantResponse handleDescription(
            String message,
            ClaimConversationDraft draft
    ) {
        if (message == null || message.trim().length() < 20) {
            return new AssistantResponse(
                    "Veuillez donner une description plus détaillée du sinistre.",
                    true,
                    false
            );
        }

        draft.setDescription(message.trim());
        draft.setStep(ClaimConversationStep.DOCUMENTS);

        return new AssistantResponse(
                "Merci. Veuillez maintenant joindre les documents disponibles.\n" +
                        "Si vous n’avez aucun document, écrivez : continuer sans document.",
                true,
                true
        );
    }

    private AssistantResponse handleDocumentsStep(
            String message,
            ClaimConversationDraft draft
    ) {
        String normalizedMessage = message.toLowerCase(Locale.ROOT).trim();

        if (normalizedMessage.contains("continuer sans document")
                || normalizedMessage.contains("sans document")) {

            draft.setStep(ClaimConversationStep.CONFIRMATION);
            return buildConfirmationMessage(draft);
        }

        return new AssistantResponse(
                "Veuillez joindre les documents avec le bouton de pièce jointe.",
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

    private AssistantResponse startClaimDeclaration(String draftKey, Long clientId) {
        ClaimConversationDraft draft = new ClaimConversationDraft();

        draft.setClientId(clientId);
        draft.setStep(ClaimConversationStep.CHOOSE_CLAIM_TYPE);

        declarationDrafts.put(draftKey, draft);

        String response = """
                Très bien, je vais vous aider à déclarer un sinistre.

                Quel type de sinistre voulez-vous déclarer ?

                - AUTO
                - SANTE
                - HABITATION
                - VOYAGE
                - VIE
                """;

        return new AssistantResponse(response, true, false);
    }

    private AssistantResponse getClientPoliciesResponse(Long clientId) {
        List<Policy> policies = policyUseCase.getPoliciesByClientId(clientId)
                .stream()
                .filter(this::isPolicyActive)
                .toList();

        if (policies.isEmpty()) {
            return new AssistantResponse(
                    "Vous n’avez aucune police d’assurance active actuellement."
            );
        }

        StringBuilder response = new StringBuilder();
        response.append("Voici vos polices d’assurance actives :\n\n");

        for (Policy policy : policies) {
            response.append("- ID ")
                    .append(policy.getId())
                    .append(" : ")
                    .append(policy.getPolicyNumber())
                    .append(" — Type : ")
                    .append(policy.getType())
                    .append("\n");
        }

        return new AssistantResponse(response.toString());
    }

    private AssistantResponse getClientClaimsResponse(Long clientId) {
        List<Claim> claims = claimUseCase.getClaimsByClientId(clientId);

        if (claims.isEmpty()) {
            return new AssistantResponse(
                    "Vous n’avez aucun dossier de sinistre enregistré actuellement."
            );
        }

        StringBuilder response = new StringBuilder();
        response.append("Voici vos dossiers de sinistre :\n\n");

        for (Claim claim : claims) {
            response.append("- Dossier #")
                    .append(claim.getId())
                    .append(" — Statut : ")
                    .append(claim.getStatus())
                    .append("\n");
        }

        return new AssistantResponse(response.toString());
    }

    private AssistantResponse generalInsuranceAnswer(String userMessage) {
        String systemPrompt = """
                Tu es l'assistant virtuel InsurFlow spécialisé dans les sinistres d'assurance.
                Réponds uniquement en français.
                Réponse courte, claire et rassurante.
                """;

        String answer = assistantChatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();

        return new AssistantResponse(answer);
    }

    private String buildDraftKey(Long clientId, String conversationId) {
        String safeConversationId =
                conversationId != null && !conversationId.isBlank()
                        ? conversationId.trim()
                        : "default";

        return clientId + ":" + safeConversationId;
    }

    private boolean isStartClaimDeclaration(String message) {
        return message.contains("déclarer un sinistre")
                || message.contains("declarer un sinistre")
                || message.contains("nouveau sinistre")
                || message.contains("je veux déclarer")
                || message.contains("je veux declarer")
                || message.contains("accident");
    }

    private boolean isPolicyRequest(String message) {
        return message.contains("police")
                || message.contains("polices")
                || message.contains("contrat")
                || message.contains("contrats");
    }

    private boolean isClaimRequest(String message) {
        return message.contains("mes sinistres")
                || message.contains("mes dossiers")
                || message.contains("réclamation")
                || message.contains("reclamation");
    }

    private String normalizeClaimType(String message) {
        if (message == null) {
            return null;
        }

        String value = message.trim().toUpperCase(Locale.ROOT);

        if (value.contains("AUTO")
                || value.contains("VOITURE")
                || value.contains("VEHICULE")
                || value.contains("ACCIDENT")) {
            return "AUTO";
        }

        if (value.contains("SANTE")
                || value.contains("SANTÉ")
                || value.contains("MEDICAL")) {
            return "SANTE";
        }

        if (value.contains("HABITATION")
                || value.contains("MAISON")
                || value.contains("LOGEMENT")) {
            return "HABITATION";
        }

        if (value.contains("VOYAGE")) {
            return "VOYAGE";
        }

        if (value.contains("VIE")) {
            return "VIE";
        }

        return null;
    }

    private boolean isSamePolicyType(String policyType, String claimType) {
        if (policyType == null || claimType == null) {
            return false;
        }

        String normalizedPolicyType = normalizeClaimType(policyType);

        return claimType.equals(normalizedPolicyType);
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
}