package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.domain.enums.ClaimStatus;
import tn.esprit.insureflow_back.domain.model.Claim;
import tn.esprit.insureflow_back.domain.port.in.HumanValidationUseCase;
import tn.esprit.insureflow_back.domain.port.out.ClaimRepositoryPort;

/**
 * Service responsable de la validation humaine des sinistres.
 * L’expert peut approuver ou rejeter un sinistre en attente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HumanValidationApplicationService
        implements HumanValidationUseCase {

    /*
     * Port utilisé pour accéder aux sinistres.
     */
    private final ClaimRepositoryPort claimRepositoryPort;

    /*
     * Service utilisé pour générer un rapport final destiné au client.
     */
    private final RapportClientService rapportClientService;

    /*
     * Approuve un sinistre après décision humaine.
     * Le statut devient APPROVED et un rapport client est généré.
     */
    @Override
    public Claim approveClaim(
            Long claimId,
            String comment,
            Double finalMin,
            Double finalAvg,
            Double finalMax
    ) {

        /*
         * Recherche du sinistre à partir de son identifiant.
         */
        Claim claim = claimRepositoryPort.findById(claimId)
                .orElseThrow(() ->
                        new RuntimeException("Claim introuvable"));

        /*
         * Vérifie que le sinistre est encore en attente de validation.
         */
        verifyPending(claim);

        /*
         * Mise à jour du statut après approbation.
         */
        claim.setStatus(ClaimStatus.APPROVED);

        /*
         * Génération du rapport client avec la décision humaine
         * et les montants finaux validés.
         */
        String clientReport =
                rapportClientService
                        .genererRapportClientApresDecisionHumaine(
                                claim,
                                "APPROUVÉ",
                                safe(comment),
                                finalMin,
                                finalAvg,
                                finalMax
                        );

        /*
         * Enregistrement du rapport généré dans le sinistre.
         */
        claim.setClientReport(clientReport);

        /*
         * Sauvegarde du sinistre approuvé.
         */
        return claimRepositoryPort.save(claim);
    }

    /*
     * Rejette un sinistre après décision humaine.
     * Le statut devient REJECTED et un rapport client est généré.
     */
    @Override
    public Claim rejectClaim(Long claimId, String comment) {

        /*
         * Recherche du sinistre à partir de son identifiant.
         */
        Claim claim = claimRepositoryPort.findById(claimId)
                .orElseThrow(() ->
                        new RuntimeException("Claim introuvable"));

        /*
         * Vérifie que le sinistre est encore en attente de validation.
         */
        verifyPending(claim);

        /*
         * Mise à jour du statut après rejet.
         */
        claim.setStatus(ClaimStatus.REJECTED);

        /*
         * Génération du rapport client avec la décision de rejet.
         * Les montants sont null car aucun remboursement n’est validé.
         */
        String clientReport =
                rapportClientService
                        .genererRapportClientApresDecisionHumaine(
                                claim,
                                "REJETÉ",
                                safe(comment),
                                null,
                                null,
                                null
                        );

        /*
         * Enregistrement du rapport généré dans le sinistre.
         */
        claim.setClientReport(clientReport);

        /*
         * Sauvegarde du sinistre rejeté.
         */
        return claimRepositoryPort.save(claim);
    }

    /*
     * Vérifie que le sinistre est dans l’état PENDING_VALIDATION.
     * Sinon, il ne peut pas être approuvé ou rejeté.
     */
    private void verifyPending(Claim claim) {

        if (!ClaimStatus.PENDING_VALIDATION.equals(claim.getStatus())) {
            throw new RuntimeException(
                    "Claim not pending"
            );
        }
    }

    /*
     * Évite les valeurs null et supprime les espaces inutiles.
     */
    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}