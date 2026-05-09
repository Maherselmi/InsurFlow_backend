package tn.esprit.insureflow_back.domain.port.out;

import tn.esprit.insureflow_back.domain.model.AiAgentConfig;

import java.util.List;
import java.util.Optional;

public interface AiAgentConfigRepositoryPort {

    AiAgentConfig save(AiAgentConfig config);

    Optional<AiAgentConfig> findById(Long id);

    Optional<AiAgentConfig> findByAgentName(String agentName);

    List<AiAgentConfig> findAll();

    void deleteById(Long id);
}