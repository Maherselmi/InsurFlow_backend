package tn.esprit.insureflow_back.domain.port.in;

import tn.esprit.insureflow_back.domain.model.ContratDocument;

import java.util.List;

public interface ContratDocumentUseCase {

    ContratDocument saveContratDocument(ContratDocument document);

    ContratDocument getContratDocumentById(String id);

    List<ContratDocument> searchContratDocuments(String query);

    void deleteContratDocument(String id);
}