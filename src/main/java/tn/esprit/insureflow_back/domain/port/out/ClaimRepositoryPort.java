package tn.esprit.insureflow_back.domain.port.out;

import tn.esprit.insureflow_back.domain.enums.ClaimStatus;
import tn.esprit.insureflow_back.domain.model.Claim;

import java.util.List;
import java.util.Optional;

public interface ClaimRepositoryPort {

    Claim save(Claim claim);

    Optional<Claim> findById(Long id);

    Optional<Claim> findByIdWithDocuments(Long id);

    List<Claim> findAll();

    List<Claim> findAllWithClient();

    List<Claim> findByStatus(ClaimStatus status);

    List<Claim> findClaimsByClientId(Long clientId);

    void deleteById(Long id);
}