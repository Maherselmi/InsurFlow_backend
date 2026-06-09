package tn.esprit.insureflow_back.application.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class AssistantResponse {

    private String answer;

    private boolean claimDeclarationMode;

    private boolean needsFileUpload;

    private boolean declarationCompleted;

    private Long claimId;

    private String status;

    private boolean processing;

    private List<AssistantAgentResultDto> agentResults = new ArrayList<>();

    public AssistantResponse(String answer) {
        this.answer = answer;
    }

    public AssistantResponse(
            String answer,
            boolean claimDeclarationMode,
            boolean needsFileUpload
    ) {
        this.answer = answer;
        this.claimDeclarationMode = claimDeclarationMode;
        this.needsFileUpload = needsFileUpload;
    }
}