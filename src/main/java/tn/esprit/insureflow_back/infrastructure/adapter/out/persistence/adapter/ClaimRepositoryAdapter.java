package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.domain.enums.ClaimStatus;
import tn.esprit.insureflow_back.domain.model.Claim;
import tn.esprit.insureflow_back.domain.port.out.ClaimRepositoryPort;
import tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository.ClaimRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ClaimRepositoryAdapter implements ClaimRepositoryPort {

    private final ClaimRepository claimRepository;

    @Override
    public Claim save(Claim claim) {
        return claimRepository.save(claim);
    }

    @Override
    public Optional<Claim> findById(Long id) {
        return claimRepository.findById(id);
    }

    @Override
    public Optional<Claim> findByIdWithDocuments(Long id) {
        return claimRepository.findByIdWithDocuments(id);
    }

    @Override
    public List<Claim> findAll() {
        return claimRepository.findAll();
    }

    @Override
    public List<Claim> findAllWithClient() {
        return claimRepository.findAllWithClient();
    }

    @Override
    public List<Claim> findByStatus(ClaimStatus status) {
        return claimRepository.findByStatus(status);
    }

    @Override
    public List<Claim> findClaimsByClientId(Long clientId) {
        return claimRepository.findClaimsByClientId(clientId);
    }

    @Override
    public void deleteById(Long id) {
        claimRepository.deleteById(id);
    }
}