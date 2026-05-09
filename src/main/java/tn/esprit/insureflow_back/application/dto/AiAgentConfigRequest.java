package tn.esprit.insureflow_back.application.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAgentConfigRequest {
    private String agentName;
    private Double confidenceThreshold;
}