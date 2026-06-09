package tn.esprit.insureflow_back.application.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.insureflow_back.domain.model.ContratDocument;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Service responsable du traitement des fichiers PDF de contrats.
 * Il extrait le texte, détecte le type du contrat et le découpe en chunks.
 */
@Slf4j
@Service
public class PdfProcessingApplicationService {

    /*
     * Traite un fichier PDF uploadé.
     * Le contenu est extrait, typé puis transformé en documents exploitables par le RAG.
     */
    public List<ContratDocument> processPDF(
            MultipartFile file,
            String requestedTypeContrat
    ) throws IOException {

        List<ContratDocument> documents = new ArrayList<>();

        /*
         * Ouverture du PDF avec PDFBox.
         * Le try-with-resources ferme automatiquement le document après traitement.
         */
        try (PDDocument document = PDDocument.load(file.getInputStream())) {

            /*
             * Extraction du texte complet du PDF.
             */
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            /*
             * Détection du type de contrat.
             * La priorité est donnée au type demandé, puis au nom du fichier,
             * puis au contenu du PDF.
             */
            String detectedType = resolveTypeContrat(
                    requestedTypeContrat,
                    file.getOriginalFilename(),
                    text
            );

            log.info("PDF={} | typeContrat détecté={}",
                    file.getOriginalFilename(),
                    detectedType
            );

            /*
             * Découpage du texte en petits blocs pour l’indexation vectorielle.
             */
            List<String> chunks = splitIntoChunks(text, 500);

            /*
             * Transformation de chaque chunk en ContratDocument.
             */
            for (int i = 0; i < chunks.size(); i++) {
                ContratDocument doc = ContratDocument.builder()
                        .id(file.getOriginalFilename() + "_chunk_" + i)
                        .fileName(file.getOriginalFilename())
                        .content(chunks.get(i))
                        .typeContrat(detectedType)
                        .pageNumber(String.valueOf(i + 1))
                        .source("PDF_UPLOAD")
                        .build();

                documents.add(doc);
            }
        }

        return documents;
    }

    /*
     * Découpe un texte en morceaux de taille fixe.
     * Chaque morceau sera ensuite stocké comme un document séparé.
     */
    private List<String> splitIntoChunks(String text, int size) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        for (int i = 0; i < text.length(); i += size) {
            int end = Math.min(text.length(), i + size);
            chunks.add(text.substring(i, end));
        }

        return chunks;
    }

    /*
     * Détermine le type du contrat.
     * La détection se fait par ordre de priorité :
     * type demandé, nom du fichier, puis contenu textuel.
     */
    private String resolveTypeContrat(
            String requestedTypeContrat,
            String fileName,
            String fullText
    ) {
        String explicitType = normalizeTypeValue(requestedTypeContrat);

        /*
         * Si le type a été fourni clairement par l’utilisateur,
         * on l’utilise directement.
         */
        if (!explicitType.equals("INCONNU")) {
            return explicitType;
        }

        /*
         * Détection du type à partir du nom du fichier.
         */
        String normalizedFileName = normalize(fileName);

        if (containsAny(
                normalizedFileName,
                "assurance_sante",
                "contrat_sante",
                "conditions_generales_assurance_sante",
                "sante"
        )) {
            return "SANTE";
        }

        if (containsAny(
                normalizedFileName,
                "assurance_auto",
                "contrat_auto",
                "conditions_generales_assurance_auto",
                "vehicule"
        )) {
            return "AUTO";
        }

        if (containsAny(
                normalizedFileName,
                "assurance_habitation",
                "contrat_habitation",
                "conditions_generales_assurance_habitation",
                "habitation"
        )) {
            return "HABITATION";
        }

        /*
         * Si le nom du fichier ne suffit pas,
         * on détecte le type à partir du contenu du PDF.
         */
        String normalizedText = normalize(fullText);

        if (isSanteContract(normalizedText)) {
            return "SANTE";
        }

        if (isAutoContract(normalizedText)) {
            return "AUTO";
        }

        if (isHabitationContract(normalizedText)) {
            return "HABITATION";
        }

        return "INCONNU";
    }

    /*
     * Normalise le type fourni par l’utilisateur.
     */
    private String normalizeTypeValue(String value) {
        String normalized = normalize(value);

        if (normalized.equals("auto")) return "AUTO";
        if (normalized.equals("sante")) return "SANTE";
        if (normalized.equals("habitation")) return "HABITATION";

        return "INCONNU";
    }

    /*
     * Vérifie si le texte correspond à un contrat santé.
     */
    private boolean isSanteContract(String text) {
        return matches(
                text,
                "\\bassurance sante\\b",
                "\\bcontrat d assurance sante\\b",
                "\\bprestataire de soins\\b",
                "\\bcnam\\b",
                "\\bhospitalisation\\b",
                "\\bconsultation medecin\\b",
                "\\bmedicaments\\b",
                "\\baffection de longue duree\\b",
                "\\bmaternite\\b"
        );
    }

    /*
     * Vérifie si le texte correspond à un contrat auto.
     */
    private boolean isAutoContract(String text) {
        return matches(
                text,
                "\\bassurance auto\\b",
                "\\bvehicule assure\\b",
                "\\bimmatriculation\\b",
                "\\bresponsabilite civile\\b",
                "\\bcollision\\b",
                "\\bvol du vehicule\\b",
                "\\bincendie du vehicule\\b",
                "\\bdommages materiels\\b"
        );
    }

    /*
     * Vérifie si le texte correspond à un contrat habitation.
     */
    private boolean isHabitationContract(String text) {
        return matches(
                text,
                "\\bassurance habitation\\b",
                "\\blogement\\b",
                "\\bdegat des eaux\\b",
                "\\bincendie\\b",
                "\\bvol\\b",
                "\\bresponsabilite civile habitation\\b"
        );
    }

    /*
     * Vérifie si le texte contient au moins une expression régulière donnée.
     */
    private boolean matches(String text, String... regexes) {
        for (String regex : regexes) {
            if (Pattern.compile(regex).matcher(text).find()) {
                return true;
            }
        }

        return false;
    }

    /*
     * Vérifie si un texte contient au moins une valeur parmi une liste.
     */
    private boolean containsAny(String text, String... values) {
        if (text == null) {
            return false;
        }

        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }

        return false;
    }

    /*
     * Normalise une chaîne :
     * suppression des accents, passage en minuscules et suppression des espaces inutiles.
     */
    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String noAccent = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return noAccent.toLowerCase(Locale.ROOT).trim();
    }
}