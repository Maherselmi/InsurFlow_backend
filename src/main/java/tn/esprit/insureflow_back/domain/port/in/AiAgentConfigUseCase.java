package tn.esprit.insureflow_back.domain.port.in;

import tn.esprit.insureflow_back.domain.model.AiAgentConfig;

import java.util.List;

public interface AiAgentConfigUseCase {

    AiAgentConfig createConfig(AiAgentConfig config);

    AiAgentConfig updateConfig(Long id, AiAgentConfig config);

    AiAgentConfig getConfigById(Long id);

    AiAgentConfig getConfigByAgentName(String agentName);

    List<AiAgentConfig> getAllConfigs();

    void deleteConfig(Long id);
}