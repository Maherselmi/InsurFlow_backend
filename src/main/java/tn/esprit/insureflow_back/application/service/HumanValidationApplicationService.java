package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.domain.enums.ClaimStatus;
import tn.esprit.insureflow_back.domain.model.Claim;
import tn.esprit.insureflow_back.domain.port.in.HumanValidationUseCase;
import tn.esprit.insureflow_back.domain.port.out.ClaimRepositoryPort;

@Slf4j
@Service
@RequiredArgsConstructor
public class HumanValidationApplicationService
        implements HumanValidationUseCase {

    private final ClaimRepositoryPort claimRepositoryPort;
    private final RapportClientService rapportClientService;

    @Override
    public Claim approveClaim(
            Long claimId,
            String comment,
            Double finalMin,
            Double finalAvg,
            Double finalMax
    ) {

        Claim claim = claimRepositoryPort.findById(claimId)
                .orElseThrow(() ->
                        new RuntimeException("Claim introuvable"));

        verifyPending(claim);

        claim.setStatus(ClaimStatus.APPROVED);

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

        claim.setClientReport(clientReport);

        return claimRepositoryPort.save(claim);
    }

    @Override
    public Claim rejectClaim(Long claimId, String comment) {

        Claim claim = claimRepositoryPort.findById(claimId)
                .orElseThrow(() ->
                        new RuntimeException("Claim introuvable"));

        verifyPending(claim);

        claim.setStatus(ClaimStatus.REJECTED);

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

        claim.setClientReport(clientReport);

        return claimRepositoryPort.save(claim);
    }

    private void verifyPending(Claim claim) {

        if (!ClaimStatus.PENDING_VALIDATION.equals(claim.getStatus())) {
            throw new RuntimeException(
                    "Claim not pending"
            );
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}