package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.domain.model.ContratVectorFile;
import tn.esprit.insureflow_back.domain.port.out.ContratVectorFileRepositoryPort;
import tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository.ContratVectorFileRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ContratVectorFileRepositoryAdapter implements ContratVectorFileRepositoryPort {

    private final ContratVectorFileRepository contratVectorFileRepository;

    @Override
    public ContratVectorFile save(ContratVectorFile vectorFile) {
        return contratVectorFileRepository.save(vectorFile);
    }

    @Override
    public Optional<ContratVectorFile> findById(Long id) {
        return contratVectorFileRepository.findById(id);
    }

    @Override
    public List<ContratVectorFile> findAll() {
        return contratVectorFileRepository.findAll();
    }

    @Override
    public List<ContratVectorFile> findByTypeContrat(String typeContrat) {
        return contratVectorFileRepository.findByTypeContratIgnoreCase(typeContrat);
    }

    @Override
    public void deleteById(Long id) {
        contratVectorFileRepository.deleteById(id);
    }
}