package tn.esprit.insureflow_back.domain.port.in;

import tn.esprit.insureflow_back.domain.model.Claim;

public interface HumanValidationUseCase {

    Claim approveClaim(
            Long claimId,
            String comment,
            Double finalEstimationMin,
            Double finalEstimationMoyenne,
            Double finalEstimationMax
    );

    Claim rejectClaim(Long claimId, String comment);
}