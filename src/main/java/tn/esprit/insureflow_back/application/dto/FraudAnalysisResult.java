package tn.esprit.insureflow_back.application.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudAnalysisResult {

    private String fraudRisk;

    private Double score;

    private List<String> reasons;

    private boolean needsHumanReview;

    private String recommendation;
}