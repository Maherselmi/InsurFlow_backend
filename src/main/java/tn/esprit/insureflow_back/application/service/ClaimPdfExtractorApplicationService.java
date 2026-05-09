package tn.esprit.insureflow_back.application.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.domain.model.Claim;
import tn.esprit.insureflow_back.domain.model.ClaimDocument;

import java.io.File;
import java.io.IOException;

@Slf4j
@Service
public class ClaimPdfExtractorApplicationService {

    public String extractTextFromClaim(Claim claim) {

        if (claim == null || claim.getDocuments() == null || claim.getDocuments().isEmpty()) {
            return "";
        }

        StringBuilder fullText = new StringBuilder();

        for (ClaimDocument document : claim.getDocuments()) {

            String filePath = document.getFilePath();

            if (filePath == null || !filePath.toLowerCase().endsWith(".pdf")) {
                continue;
            }

            log.info("Extraction PDF : {}", document.getFileName());

            try {
                File pdfFile = new File(filePath);

                if (!pdfFile.exists()) {
                    log.warn("Fichier introuvable : {}", filePath);
                    continue;
                }

                try (PDDocument pdDocument = PDDocument.load(pdfFile)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    String text = stripper.getText(pdDocument);

                    fullText.append("=== Fichier: ")
                            .append(document.getFileName())
                            .append(" ===\n");

                    fullText.append(text).append("\n\n");

                    log.info("Texte extrait : {} caractères", text.length());
                }

            } catch (IOException e) {
                log.error(
                        "Erreur extraction PDF {} : {}",
                        document.getFileName(),
                        e.getMessage()
                );
            }
        }

        return fullText.toString();
    }
}