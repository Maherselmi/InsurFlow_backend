package tn.esprit.insureflow_back.domain.port.out;

import org.springframework.data.domain.PageRequest;
import tn.esprit.insureflow_back.domain.enums.AgentName;
import tn.esprit.insureflow_back.domain.model.AgentLearningFeedback;

import java.util.List;
import java.util.Optional;

public interface AgentLearningFeedbackRepositoryPort {

    AgentLearningFeedback save(AgentLearningFeedback feedback);

    Optional<AgentLearningFeedback> findById(Long id);

    Optional<AgentLearningFeedback> findByClaimIdAndAgentName(Long claimId, AgentName agentName);

    List<AgentLearningFeedback> findByClaimId(Long claimId);

    List<AgentLearningFeedback> findAll();

    void deleteById(Long id);

    List<AgentLearningFeedback> findLearningExamples(
            AgentName agentName,
            Long currentClaimId,
            PageRequest pageRequest
    );

}