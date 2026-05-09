package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.insureflow_back.domain.model.ExpertFeedback;

import java.util.Optional;

public interface ExpertFeedbackRepository extends JpaRepository<ExpertFeedback, Long> {
    Optional<ExpertFeedback> findByClaimId(Long claimId);
}