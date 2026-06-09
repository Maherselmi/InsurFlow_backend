package tn.esprit.insureflow_back.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantAgentResultDto {

    private String agentName;

    private String title;

    private String conclusion;

    private Double confidenceScore;

    private boolean needsHumanReview;
}