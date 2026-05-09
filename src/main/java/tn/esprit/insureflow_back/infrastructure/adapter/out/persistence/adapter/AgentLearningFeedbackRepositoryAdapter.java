package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.domain.enums.AgentName;
import tn.esprit.insureflow_back.domain.model.AgentLearningFeedback;
import tn.esprit.insureflow_back.domain.port.out.AgentLearningFeedbackRepositoryPort;
import tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository.AgentLearningFeedbackRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AgentLearningFeedbackRepositoryAdapter implements AgentLearningFeedbackRepositoryPort {

    private final AgentLearningFeedbackRepository agentLearningFeedbackRepository;

    @Override
    public AgentLearningFeedback save(AgentLearningFeedback feedback) {
        return agentLearningFeedbackRepository.save(feedback);
    }

    @Override
    public Optional<AgentLearningFeedback> findById(Long id) {
        return agentLearningFeedbackRepository.findById(id);
    }

    @Override
    public Optional<AgentLearningFeedback> findByClaimIdAndAgentName(
            Long claimId,
            AgentName agentName
    ) {
        return agentLearningFeedbackRepository.findByClaim_IdAndAgentName(claimId, agentName);
    }

    @Override
    public List<AgentLearningFeedback> findByClaimId(Long claimId) {
        return agentLearningFeedbackRepository.findAll()
                .stream()
                .filter(feedback -> feedback.getClaim() != null
                        && feedback.getClaim().getId() != null
                        && feedback.getClaim().getId().equals(claimId))
                .toList();
    }

    @Override
    public List<AgentLearningFeedback> findAll() {
        return agentLearningFeedbackRepository.findAll();
    }

    @Override
    public List<AgentLearningFeedback> findLearningExamples(
            AgentName agentName,
            Long currentClaimId,
            PageRequest pageRequest
    ) {
        return agentLearningFeedbackRepository.findLearningExamples(
                agentName,
                currentClaimId,
                pageRequest
        );
    }

    @Override
    public void deleteById(Long id) {
        agentLearningFeedbackRepository.deleteById(id);
    }
}