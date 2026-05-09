package tn.esprit.insureflow_back.infrastructure.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.insureflow_back.application.service.ContratVectorApplicationService;
import tn.esprit.insureflow_back.application.service.PdfProcessingApplicationService;
import tn.esprit.insureflow_back.domain.model.ContratDocument;
import tn.esprit.insureflow_back.domain.model.ContratVectorFile;
import tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository.ContratVectorFileRepository;


import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/contrats")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class ContratController {

    private final PdfProcessingApplicationService pdfProcessingService;
    private final ContratVectorApplicationService contratVectorService;
    private final ContratVectorFileRepository contratVectorFileRepository;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadPDF(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "typeContrat", required = false) String typeContrat
    ) {
        try {
            List<ContratDocument> docs = pdfProcessingService.processPDF(file, typeContrat);
            contratVectorService.saveToVectorDB(docs);

            return ResponseEntity.ok("✅ PDF uploadé et injecté dans Milvus");

        } catch (IOException e) {
            return ResponseEntity
                    .internalServerError()
                    .body("❌ Erreur traitement PDF: " + e.getMessage());
        }
    }

    @GetMapping("/documents")
    public ResponseEntity<List<ContratVectorFile>> getVectorDocuments(
            @RequestParam(value = "typeContrat", required = false) String typeContrat
    ) {
        if (typeContrat != null && !typeContrat.trim().isEmpty()) {
            return ResponseEntity.ok(
                    contratVectorFileRepository.findByTypeContratIgnoreCase(typeContrat.trim())
            );
        }

        return ResponseEntity.ok(contratVectorFileRepository.findAll());
    }
}