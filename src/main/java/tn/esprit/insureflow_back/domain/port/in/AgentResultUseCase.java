package tn.esprit.insureflow_back.domain.port.in;

import tn.esprit.insureflow_back.domain.model.AgentResult;

import java.util.List;

public interface AgentResultUseCase {

    AgentResult saveAgentResult(AgentResult result);

    AgentResult getAgentResultById(Long id);

    List<AgentResult> getAllAgentResults();

    List<AgentResult> getAgentResultsByClaimId(Long claimId);

    void deleteAgentResult(Long id);
}