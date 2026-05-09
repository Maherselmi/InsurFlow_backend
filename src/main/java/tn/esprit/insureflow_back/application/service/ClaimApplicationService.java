package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.insureflow_back.application.dto.ClaimConversationDraft;
import tn.esprit.insureflow_back.application.dto.DraftDocument;
import tn.esprit.insureflow_back.domain.enums.ClaimStatus;
import tn.esprit.insureflow_back.domain.model.Claim;
import tn.esprit.insureflow_back.domain.model.ClaimDocument;
import tn.esprit.insureflow_back.domain.model.Client;
import tn.esprit.insureflow_back.domain.model.Policy;
import tn.esprit.insureflow_back.domain.port.in.ClaimUseCase;
import tn.esprit.insureflow_back.domain.port.out.ClaimDocumentRepositoryPort;
import tn.esprit.insureflow_back.domain.port.out.ClaimRepositoryPort;
import tn.esprit.insureflow_back.domain.port.out.ClientRepositoryPort;
import tn.esprit.insureflow_back.domain.port.out.FileStoragePort;
import tn.esprit.insureflow_back.domain.port.out.PolicyRepositoryPort;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ClaimApplicationService implements ClaimUseCase {

    private final ClaimRepositoryPort claimRepositoryPort;
    private final PolicyRepositoryPort policyRepositoryPort;
    private final ClientRepositoryPort clientRepositoryPort;
    private final ClaimDocumentRepositoryPort claimDocumentRepositoryPort;
    private final FileStoragePort fileStoragePort;

    @Override
    public Claim createClaim(Claim claim) {
        if (claim == null) {
            throw new RuntimeException("Claim is required");
        }

        if (claim.getPolicy() != null && claim.getPolicy().getId() != null) {
            Policy existingPolicy = policyRepositoryPort.findById(claim.getPolicy().getId())
                    .orElseThrow(() -> new RuntimeException("Policy not found"));

            claim.setPolicy(existingPolicy);

            if (existingPolicy.getClient() != null) {
                claim.setClient(existingPolicy.getClient());
            }
        }

        claim.setStatus(ClaimStatus.PENDING_VALIDATION);

        return claimRepositoryPort.save(claim);
    }

    @Override
    public Claim createClaimFromConversation(ClaimConversationDraft draft) {
        validateDraft(draft);

        Client client = clientRepositoryPort.findById(draft.getClientId())
                .orElseThrow(() -> new RuntimeException("Client not found"));

        Policy policy = policyRepositoryPort.findById(draft.getPolicyId())
                .orElseThrow(() -> new RuntimeException("Policy not found"));

        if (policy.getClient() == null || !policy.getClient().getId().equals(client.getId())) {
            throw new RuntimeException("Cette police n'appartient pas au client connecté");
        }

        Claim claim = new Claim();
        claim.setClient(client);
        claim.setPolicy(policy);
        claim.setIncidentDate(draft.getIncidentDate());
        claim.setDescription(draft.getDescription());
        claim.setStatus(ClaimStatus.PENDING_VALIDATION);

        Claim savedClaim = claimRepositoryPort.save(claim);

        saveDraftDocuments(savedClaim, draft.getDocuments());

        return savedClaim;
    }

    @Override
    public List<Claim> getAllClaims() {
        return claimRepositoryPort.findAllWithClient();
    }

    @Override
    public Claim getClaimById(Long id) {
        return claimRepositoryPort.findById(id)
                .orElseThrow(() -> new RuntimeException("Claim not found with id: " + id));
    }

    @Override
    public List<Claim> getClaimsByClientId(Long clientId) {
        if (clientId == null) {
            throw new RuntimeException("Client id is required");
        }

        clientRepositoryPort.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        return claimRepositoryPort.findClaimsByClientId(clientId);
    }

    @Override
    public List<Claim> getPendingValidation() {
        return claimRepositoryPort.findByStatus(ClaimStatus.PENDING_VALIDATION);
    }

    @Override
    public Claim getClaimReports(Long id) {
        return getClaimById(id);
    }

    @Override
    public void deleteClaim(Long id) {
        claimRepositoryPort.deleteById(id);
    }

    private void validateDraft(ClaimConversationDraft draft) {
        if (draft == null) {
            throw new RuntimeException("Déclaration invalide");
        }

        if (draft.getClientId() == null) {
            throw new RuntimeException("Client manquant");
        }

        if (draft.getPolicyId() == null) {
            throw new RuntimeException("Police manquante");
        }

        if (draft.getIncidentDate() == null) {
            throw new RuntimeException("Date incident manquante");
        }

        if (draft.getDescription() == null || draft.getDescription().isBlank()) {
            throw new RuntimeException("Description manquante");
        }
    }

    private void saveDraftDocuments(Claim claim, List<DraftDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        for (DraftDocument draftDocument : documents) {
            if (draftDocument == null
                    || draftDocument.getContent() == null
                    || draftDocument.getContent().length == 0) {
                continue;
            }

            String originalFileName = draftDocument.getFileName() != null
                    ? draftDocument.getFileName()
                    : "document";

            String filePath = fileStoragePort.saveFile(
                    originalFileName,
                    draftDocument.getContentType(),
                    draftDocument.getContent()
            );

            ClaimDocument claimDocument = new ClaimDocument();
            claimDocument.setClaim(claim);
            claimDocument.setFileName(originalFileName);
            claimDocument.setFileType(draftDocument.getContentType());
            claimDocument.setFilePath(filePath);

            claimDocumentRepositoryPort.save(claimDocument);
        }
    }
}