package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.domain.model.ExpertFeedback;
import tn.esprit.insureflow_back.domain.port.out.ExpertFeedbackRepositoryPort;
import tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository.ExpertFeedbackRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ExpertFeedbackRepositoryAdapter implements ExpertFeedbackRepositoryPort {

    private final ExpertFeedbackRepository expertFeedbackRepository;

    @Override
    public ExpertFeedback save(ExpertFeedback feedback) {
        return expertFeedbackRepository.save(feedback);
    }

    @Override
    public Optional<ExpertFeedback> findById(Long id) {
        return expertFeedbackRepository.findById(id);
    }

    @Override
    public Optional<ExpertFeedback> findByClaimId(Long claimId) {
        return expertFeedbackRepository.findByClaimId(claimId);
    }

    @Override
    public List<ExpertFeedback> findAll() {
        return expertFeedbackRepository.findAll();
    }

    @Override
    public void deleteById(Long id) {
        expertFeedbackRepository.deleteById(id);
    }
}