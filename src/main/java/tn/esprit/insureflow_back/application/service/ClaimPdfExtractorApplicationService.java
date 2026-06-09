package tn.esprit.insureflow_back.application.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.domain.model.Claim;
import tn.esprit.insureflow_back.domain.model.ClaimDocument;

import java.io.File;
import java.io.IOException;

/**
 * Service responsable de l’extraction du texte depuis les fichiers PDF
 * associés à un sinistre.
 */
@Slf4j
@Service
public class ClaimPdfExtractorApplicationService {

    /*
     * Parcourt les documents d’un sinistre et extrait le texte
     * uniquement depuis les fichiers PDF valides.
     */
    public String extractTextFromClaim(Claim claim) {

        /*
         * Si le sinistre ou ses documents sont absents,
         * aucun texte ne peut être extrait.
         */
        if (claim == null || claim.getDocuments() == null || claim.getDocuments().isEmpty()) {
            return "";
        }

        /*
         * Contient le texte complet extrait de tous les PDF du sinistre.
         */
        StringBuilder fullText = new StringBuilder();

        /*
         * Parcours de tous les documents liés au sinistre.
         */
        for (ClaimDocument document : claim.getDocuments()) {

            String filePath = document.getFilePath();

            /*
             * Ignore les documents sans chemin ou qui ne sont pas des fichiers PDF.
             */
            if (filePath == null || !filePath.toLowerCase().endsWith(".pdf")) {
                continue;
            }

            log.info("Extraction PDF : {}", document.getFileName());

            try {
                File pdfFile = new File(filePath);

                /*
                 * Vérifie que le fichier existe réellement sur le disque.
                 */
                if (!pdfFile.exists()) {
                    log.warn("Fichier introuvable : {}", filePath);
                    continue;
                }

                /*
                 * Ouvre le PDF avec PDFBox et extrait son contenu textuel.
                 * try-with-resources ferme automatiquement le document après usage.
                 */
                try (PDDocument pdDocument = PDDocument.load(pdfFile)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    String text = stripper.getText(pdDocument);

                    /*
                     * Ajoute un titre pour identifier le fichier source du texte extrait.
                     */
                    fullText.append("=== Fichier: ")
                            .append(document.getFileName())
                            .append(" ===\n");

                    fullText.append(text).append("\n\n");

                    log.info("Texte extrait : {} caractères", text.length());
                }

            } catch (IOException e) {
                /*
                 * En cas d’erreur de lecture PDF, on log l’erreur
                 * sans arrêter le traitement des autres documents.
                 */
                log.error(
                        "Erreur extraction PDF {} : {}",
                        document.getFileName(),
                        e.getMessage()
                );
            }
        }

        /*
         * Retourne tout le texte extrait depuis les PDF du sinistre.
         */
        return fullText.toString();
    }
}