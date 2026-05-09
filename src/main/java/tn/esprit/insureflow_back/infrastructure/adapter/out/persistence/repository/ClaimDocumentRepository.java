package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.insureflow_back.domain.model.ClaimDocument;

import java.util.List;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, Long> {
    List<ClaimDocument> findByClaimId(Long claimId);
}