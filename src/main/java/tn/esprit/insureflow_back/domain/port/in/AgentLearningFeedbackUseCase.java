package tn.esprit.insureflow_back.domain.port.in;

import tn.esprit.insureflow_back.domain.enums.AgentName;
import tn.esprit.insureflow_back.domain.model.AgentLearningFeedback;

import java.util.List;

public interface AgentLearningFeedbackUseCase {

    AgentLearningFeedback saveFeedback(AgentLearningFeedback feedback);

    AgentLearningFeedback getFeedbackById(Long id);

    AgentLearningFeedback getFeedbackByClaimIdAndAgentName(Long claimId, AgentName agentName);

    List<AgentLearningFeedback> getFeedbacksByClaimId(Long claimId);

    List<AgentLearningFeedback> getAllFeedbacks();

    void deleteFeedback(Long id);
}