package tn.esprit.insureflow_back.application.dto;

import lombok.Data;

@Data
public class AssistantRequest {
    private String message;
    private Long clientId;
    private String conversationId;

}