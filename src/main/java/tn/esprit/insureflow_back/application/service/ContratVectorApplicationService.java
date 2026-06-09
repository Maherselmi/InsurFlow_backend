package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.domain.model.ContratDocument;
import tn.esprit.insureflow_back.domain.model.ContratVectorFile;
import tn.esprit.insureflow_back.domain.port.out.ContratVectorFileRepositoryPort;
import tn.esprit.insureflow_back.domain.port.out.VectorStorePort;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service responsable de l’injection des documents de contrat
 * dans la base vectorielle et de l’enregistrement de leurs métadonnées.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContratVectorApplicationService {

    /*
     * Port utilisé pour stocker les chunks du contrat dans la base vectorielle.
     */
    private final VectorStorePort vectorStorePort;

    /*
     * Port utilisé pour enregistrer les informations du fichier injecté en SQL.
     */
    private final ContratVectorFileRepositoryPort contratVectorFileRepositoryPort;

    /*
     * Injecte une liste de documents de contrat dans la base vectorielle.
     * Chaque document représente généralement un chunk extrait du PDF.
     */
    public void saveToVectorDB(List<ContratDocument> docs) {
        if (docs == null || docs.isEmpty()) {
            throw new IllegalArgumentException("Aucun document à injecter.");
        }

        /*
         * Stockage de chaque chunk dans le VectorStore.
         */
        for (ContratDocument doc : docs) {
            vectorStorePort.storeDocument(
                    doc.getId(),
                    doc.getContent()
            );
        }

        /*
         * Le premier document est utilisé pour récupérer les informations communes
         * du fichier source : nom, type de contrat et origine.
         */
        ContratDocument first = docs.get(0);

        /*
         * Calcul du nombre de pages différentes présentes dans les documents.
         */
        int pagesCount = docs.stream()
                .map(ContratDocument::getPageNumber)
                .filter(page -> page != null)
                .collect(java.util.stream.Collectors.toSet())
                .size();

        /*
         * Création de l’enregistrement SQL contenant les métadonnées du fichier injecté.
         */
        ContratVectorFile fileRecord = ContratVectorFile.builder()
                .fileName(first.getFileName())
                .typeContrat(first.getTypeContrat())
                .source(first.getSource())
                .pagesCount(pagesCount)
                .chunksCount(docs.size())
                .uploadedAt(LocalDateTime.now())
                .build();

        /*
         * Sauvegarde des métadonnées du fichier dans la base relationnelle.
         */
        contratVectorFileRepositoryPort.save(fileRecord);

        /*
         * Log de confirmation après l’injection dans la base vectorielle et SQL.
         */
        log.info("PDF injecté dans VectorStore et enregistré en SQL: {}",
                first.getFileName()
        );
    }
}