package tn.esprit.insureflow_back.domain.port.out;

import tn.esprit.insureflow_back.domain.model.ClaimDocument;

import java.util.List;
import java.util.Optional;

public interface ClaimDocumentRepositoryPort {

    ClaimDocument save(ClaimDocument document);

    Optional<ClaimDocument> findById(Long id);

    List<ClaimDocument> findAll();

    List<ClaimDocument> findByClaimId(Long claimId);

    void deleteById(Long id);
}