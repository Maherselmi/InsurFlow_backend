package tn.esprit.insureflow_back.domain.port.out;

import tn.esprit.insureflow_back.domain.model.ContratVectorFile;

import java.util.List;
import java.util.Optional;

public interface ContratVectorFileRepositoryPort {

    ContratVectorFile save(ContratVectorFile vectorFile);

    Optional<ContratVectorFile> findById(Long id);

    List<ContratVectorFile> findAll();

    List<ContratVectorFile> findByTypeContrat(String typeContrat);

    void deleteById(Long id);
}