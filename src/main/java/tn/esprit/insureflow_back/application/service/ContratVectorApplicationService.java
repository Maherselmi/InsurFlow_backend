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

@Service
@RequiredArgsConstructor
@Slf4j
public class ContratVectorApplicationService {

    private final VectorStorePort vectorStorePort;
    private final ContratVectorFileRepositoryPort contratVectorFileRepositoryPort;

    public void saveToVectorDB(List<ContratDocument> docs) {
        if (docs == null || docs.isEmpty()) {
            throw new IllegalArgumentException("Aucun document à injecter.");
        }

        for (ContratDocument doc : docs) {
            vectorStorePort.storeDocument(
                    doc.getId(),
                    doc.getContent()
            );
        }

        ContratDocument first = docs.get(0);

        int pagesCount = docs.stream()
                .map(ContratDocument::getPageNumber)
                .filter(page -> page != null)
                .collect(java.util.stream.Collectors.toSet())
                .size();

        ContratVectorFile fileRecord = ContratVectorFile.builder()
                .fileName(first.getFileName())
                .typeContrat(first.getTypeContrat())
                .source(first.getSource())
                .pagesCount(pagesCount)
                .chunksCount(docs.size())
                .uploadedAt(LocalDateTime.now())
                .build();

        contratVectorFileRepositoryPort.save(fileRecord);

        log.info("PDF injecté dans VectorStore et enregistré en SQL: {}",
                first.getFileName()
        );
    }
}