package tn.esprit.insureflow_back.domain.port.out;

import tn.esprit.insureflow_back.domain.model.AgentResult;

import java.util.List;
import java.util.Optional;

public interface AgentResultRepositoryPort {

    AgentResult save(AgentResult result);

    Optional<AgentResult> findById(Long id);

    List<AgentResult> findAll();

    List<AgentResult> findByClaimId(Long claimId);

    void deleteById(Long id);
}