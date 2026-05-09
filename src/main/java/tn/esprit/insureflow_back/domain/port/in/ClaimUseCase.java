package tn.esprit.insureflow_back.domain.port.in;

import tn.esprit.insureflow_back.application.dto.ClaimConversationDraft;
import tn.esprit.insureflow_back.domain.model.Claim;

import java.util.List;

public interface ClaimUseCase {

    Claim createClaim(Claim claim);

    Claim getClaimById(Long id);

    List<Claim> getAllClaims();

    List<Claim> getClaimsByClientId(Long clientId);

    List<Claim> getPendingValidation();

    Claim getClaimReports(Long id);

    void deleteClaim(Long id);

    Claim createClaimFromConversation(ClaimConversationDraft draft);
}