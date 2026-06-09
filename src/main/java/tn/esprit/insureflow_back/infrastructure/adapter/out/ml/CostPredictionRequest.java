package tn.esprit.insureflow_back.infrastructure.adapter.out.ml;

public record CostPredictionRequest(
        String type,
        String severity,
        String damage_part,
        String description,
        int fraud
) {
}