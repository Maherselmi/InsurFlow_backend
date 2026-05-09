package tn.esprit.insureflow_back.domain.port.in;

import tn.esprit.insureflow_back.domain.model.ContratVectorFile;

import java.util.List;

public interface ContratVectorFileUseCase {

    ContratVectorFile saveVectorFile(ContratVectorFile vectorFile);

    ContratVectorFile getVectorFileById(Long id);

    List<ContratVectorFile> getAllVectorFiles();

    List<ContratVectorFile> getVectorFilesByTypeContrat(String typeContrat);

    void deleteVectorFile(Long id);
}