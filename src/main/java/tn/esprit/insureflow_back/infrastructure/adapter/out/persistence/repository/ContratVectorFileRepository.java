package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.insureflow_back.domain.model.ContratVectorFile;

import java.util.List;

public interface ContratVectorFileRepository extends JpaRepository<ContratVectorFile, Long> {

    List<ContratVectorFile> findByTypeContratIgnoreCase(String typeContrat);
}