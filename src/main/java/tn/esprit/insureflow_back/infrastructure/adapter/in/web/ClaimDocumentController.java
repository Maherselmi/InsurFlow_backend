package tn.esprit.insureflow_back.infrastructure.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.insureflow_back.application.Orchestrator.ClaimOrchestrator;
import tn.esprit.insureflow_back.application.service.ClaimDocumentApplicationService;
import tn.esprit.insureflow_back.domain.model.Claim;
import tn.esprit.insureflow_back.domain.model.ClaimDocument;
import tn.esprit.insureflow_back.domain.port.out.ClaimRepositoryPort;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class ClaimDocumentController {

    private final ClaimDocumentApplicationService documentService;
    private final ClaimOrchestrator claimOrchestrator;
    private final ClaimRepositoryPort claimRepositoryPort;

    @PostMapping("/upload/{claimId}")
    public ResponseEntity<ClaimDocument> upload(
            @PathVariable Long claimId,
            @RequestParam("file") MultipartFile file
    ) throws Exception {
        return ResponseEntity.ok(documentService.uploadFile(claimId, file));
    }

    @PostMapping("/process/{claimId}")
    public ResponseEntity<String> processClaim(@PathVariable Long claimId) {
        Claim claim = claimRepositoryPort.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim not found"));

        claimOrchestrator.processClaim(claim);

        return ResponseEntity.ok("Traitement démarré pour claim #" + claimId);
    }
}