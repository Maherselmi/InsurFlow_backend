package tn.esprit.insureflow_back.domain.port.out;

import tn.esprit.insureflow_back.domain.model.ExpertFeedback;

import java.util.List;
import java.util.Optional;

public interface ExpertFeedbackRepositoryPort {

    ExpertFeedback save(ExpertFeedback feedback);

    Optional<ExpertFeedback> findById(Long id);

    Optional<ExpertFeedback> findByClaimId(Long claimId);

    List<ExpertFeedback> findAll();

    void deleteById(Long id);
}