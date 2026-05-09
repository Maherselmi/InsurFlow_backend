package tn.esprit.insureflow_back.domain.port.out;

public interface LlmPort {

    String generateResponse(String prompt);

    String analyzeClaim(String claimContext);

    String validateClaim(String claimContext);

    String estimateClaim(String claimContext);
}