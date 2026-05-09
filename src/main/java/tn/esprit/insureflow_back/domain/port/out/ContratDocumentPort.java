package tn.esprit.insureflow_back.domain.port.out;

import tn.esprit.insureflow_back.domain.model.ContratDocument;

import java.util.List;
import java.util.Optional;

public interface ContratDocumentPort {

    ContratDocument save(ContratDocument document);

    Optional<ContratDocument> findById(String id);

    List<ContratDocument> searchSimilar(String query);

    void deleteById(String id);
}