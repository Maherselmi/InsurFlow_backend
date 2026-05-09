package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.domain.model.ClaimDocument;
import tn.esprit.insureflow_back.domain.port.out.ClaimDocumentRepositoryPort;
import tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository.ClaimDocumentRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ClaimDocumentRepositoryAdapter implements ClaimDocumentRepositoryPort {

    private final ClaimDocumentRepository claimDocumentRepository;

    @Override
    public ClaimDocument save(ClaimDocument document) {
        return claimDocumentRepository.save(document);
    }

    @Override
    public Optional<ClaimDocument> findById(Long id) {
        return claimDocumentRepository.findById(id);
    }

    @Override
    public List<ClaimDocument> findAll() {
        return claimDocumentRepository.findAll();
    }

    @Override
    public List<ClaimDocument> findByClaimId(Long claimId) {
        return claimDocumentRepository.findByClaimId(claimId);
    }

    @Override
    public void deleteById(Long id) {
        claimDocumentRepository.deleteById(id);
    }
}