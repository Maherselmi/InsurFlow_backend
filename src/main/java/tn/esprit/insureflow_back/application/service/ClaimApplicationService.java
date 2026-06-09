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

/**
 * Service applicatif responsable de la gestion des sinistres.
 * Il permet de créer, consulter, supprimer les sinistres et gérer leurs documents.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ClaimApplicationService implements ClaimUseCase {

    /*
     * Ports utilisés pour accéder aux données des sinistres,
     * clients, polices, documents et stockage des fichiers.
     */
    private final ClaimRepositoryPort claimRepositoryPort;
    private final PolicyRepositoryPort policyRepositoryPort;
    private final ClientRepositoryPort clientRepositoryPort;
    private final ClaimDocumentRepositoryPort claimDocumentRepositoryPort;
    private final FileStoragePort fileStoragePort;

    /*
     * Crée un sinistre classique.
     * Si une police est fournie, elle est récupérée depuis la base
     * et le client associé est automatiquement lié au sinistre.
     */
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

    /*
     * Crée un sinistre à partir d’un brouillon généré par une conversation.
     * Cette méthode vérifie le client, la police et les données obligatoires.
     */
    @Override
    public Claim createClaimFromConversation(ClaimConversationDraft draft) {
        validateDraft(draft);

        Client client = clientRepositoryPort.findById(draft.getClientId())
                .orElseThrow(() -> new RuntimeException("Client not found"));

        Policy policy = policyRepositoryPort.findById(draft.getPolicyId())
                .orElseThrow(() -> new RuntimeException("Policy not found"));

        /*
         * Vérifie que la police sélectionnée appartient bien au client connecté.
         */
        if (policy.getClient() == null || !policy.getClient().getId().equals(client.getId())) {
            throw new RuntimeException("Cette police n'appartient pas au client connecté");
        }

        /*
         * Création du sinistre avec les données extraites de la conversation.
         */
        Claim claim = new Claim();
        claim.setClient(client);
        claim.setPolicy(policy);
        claim.setIncidentDate(draft.getIncidentDate());
        claim.setDescription(draft.getDescription());
        claim.setStatus(ClaimStatus.PENDING_VALIDATION);

        Claim savedClaim = claimRepositoryPort.save(claim);

        /*
         * Sauvegarde des documents envoyés avec la déclaration.
         */
        saveDraftDocuments(savedClaim, draft.getDocuments());

        return savedClaim;
    }

    /*
     * Récupère tous les sinistres avec les informations client.
     */
    @Override
    public List<Claim> getAllClaims() {
        return claimRepositoryPort.findAllWithClient();
    }

    /*
     * Récupère un sinistre par son identifiant.
     */
    @Override
    public Claim getClaimById(Long id) {
        return claimRepositoryPort.findById(id)
                .orElseThrow(() -> new RuntimeException("Claim not found with id: " + id));
    }

    /*
     * Récupère tous les sinistres d’un client donné.
     * L’existence du client est vérifiée avant la recherche.
     */
    @Override
    public List<Claim> getClaimsByClientId(Long clientId) {
        if (clientId == null) {
            throw new RuntimeException("Client id is required");
        }

        clientRepositoryPort.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        return claimRepositoryPort.findClaimsByClientId(clientId);
    }

    /*
     * Récupère les sinistres qui attendent encore une validation.
     */
    @Override
    public List<Claim> getPendingValidation() {
        return claimRepositoryPort.findByStatus(ClaimStatus.PENDING_VALIDATION);
    }

    /*
     * Retourne les informations d’un sinistre utilisées pour les rapports.
     */
    @Override
    public Claim getClaimReports(Long id) {
        return getClaimById(id);
    }

    /*
     * Supprime un sinistre à partir de son identifiant.
     */
    @Override
    public void deleteClaim(Long id) {
        claimRepositoryPort.deleteById(id);
    }

    /*
     * Vérifie que le brouillon de déclaration contient toutes les données obligatoires.
     */
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

    /*
     * Sauvegarde les documents associés au sinistre.
     * Les fichiers vides ou invalides sont ignorés.
     */
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

            /*
             * Utilise le nom original du fichier si disponible,
             * sinon un nom par défaut est appliqué.
             */
            String originalFileName = draftDocument.getFileName() != null
                    ? draftDocument.getFileName()
                    : "document";

            /*
             * Stockage physique du fichier via le port de stockage.
             */
            String filePath = fileStoragePort.saveFile(
                    originalFileName,
                    draftDocument.getContentType(),
                    draftDocument.getContent()
            );

            /*
             * Création de l’enregistrement du document lié au sinistre.
             */
            ClaimDocument claimDocument = new ClaimDocument();
            claimDocument.setClaim(claim);
            claimDocument.setFileName(originalFileName);
            claimDocument.setFileType(draftDocument.getContentType());
            claimDocument.setFilePath(filePath);

            claimDocumentRepositoryPort.save(claimDocument);
        }
    }
}