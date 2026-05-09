package tn.esprit.insureflow_back.application.dto;

public record HumanDecisionRequest(
        String comment,
        Double finalEstimationMin,
        Double finalEstimationMoyenne,
        Double finalEstimationMax
) {}