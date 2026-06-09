package tn.esprit.insureflow_back.infrastructure.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.insureflow_back.application.dto.AssistantRequest;
import tn.esprit.insureflow_back.application.dto.AssistantResponse;
import tn.esprit.insureflow_back.application.service.AssistantApplicationService;

import java.util.List;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class AssistantController {

    private final AssistantApplicationService assistantService;

    @PostMapping("/chat")
    public ResponseEntity<AssistantResponse> chat(@RequestBody AssistantRequest request) {
        return ResponseEntity.ok(assistantService.ask(request));
    }

    @PostMapping(
            value = "/claim-documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<AssistantResponse> uploadClaimDocuments(
            @RequestParam Long clientId,
            @RequestParam(required = false) String conversationId,
            @RequestParam("documents") List<MultipartFile> documents
    ) {
        return ResponseEntity.ok(
                assistantService.uploadClaimDocuments(clientId, conversationId, documents)
        );
    }

    @GetMapping("/claim-agent-results/{claimId}")
    public ResponseEntity<AssistantResponse> getClaimAgentResults(@PathVariable Long claimId) {
        return ResponseEntity.ok(
                assistantService.getClaimAgentResults(claimId)
        );
    }
}