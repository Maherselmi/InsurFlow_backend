package tn.esprit.insureflow_back.domain.port.out;

import java.util.List;

public interface VectorStorePort {

    void storeDocument(String documentId, String content);

    List<String> searchSimilarDocuments(String query);

    void deleteDocument(String documentId);
}