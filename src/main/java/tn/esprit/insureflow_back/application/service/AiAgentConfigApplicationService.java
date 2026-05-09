package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.domain.model.AiAgentConfig;
import tn.esprit.insureflow_back.domain.port.in.AiAgentConfigUseCase;
import tn.esprit.insureflow_back.domain.port.out.AiAgentConfigRepositoryPort;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiAgentConfigApplicationService implements AiAgentConfigUseCase {

    private final AiAgentConfigRepositoryPort aiAgentConfigRepositoryPort;

    @Override
    public AiAgentConfig createConfig(AiAgentConfig config) {
        return aiAgentConfigRepositoryPort.save(config);
    }

    @Override
    public AiAgentConfig updateConfig(Long id, AiAgentConfig config) {
        AiAgentConfig existing = getConfigById(id);

        existing.setAgentName(config.getAgentName());
        existing.setConfidenceThreshold(config.getConfidenceThreshold());

        validateThreshold(existing.getConfidenceThreshold());

        return aiAgentConfigRepositoryPort.save(existing);
    }

    public AiAgentConfig updateThreshold(String agentName, Double threshold) {
        validateThreshold(threshold);

        AiAgentConfig config = aiAgentConfigRepositoryPort.findByAgentName(agentName)
                .orElse(
                        AiAgentConfig.builder()
                                .agentName(agentName)
                                .confidenceThreshold(threshold)
                                .build()
                );

        config.setConfidenceThreshold(threshold);

        return aiAgentConfigRepositoryPort.save(config);
    }

    public double getThreshold(String agentName) {
        return aiAgentConfigRepositoryPort.findByAgentName(agentName)
                .map(AiAgentConfig::getConfidenceThreshold)
                .orElseGet(() -> getDefaultThreshold(agentName));
    }

    @Override
    public AiAgentConfig getConfigById(Long id) {
        return aiAgentConfigRepositoryPort.findById(id)
                .orElseThrow(() -> new RuntimeException("Configuration agent introuvable"));
    }

    @Override
    public AiAgentConfig getConfigByAgentName(String agentName) {
        return aiAgentConfigRepositoryPort.findByAgentName(agentName)
                .orElseThrow(() -> new RuntimeException("Configuration agent introuvable"));
    }

    @Override
    public List<AiAgentConfig> getAllConfigs() {
        return aiAgentConfigRepositoryPort.findAll();
    }

    @Override
    public void deleteConfig(Long id) {
        aiAgentConfigRepositoryPort.deleteById(id);
    }

    private void validateThreshold(Double threshold) {
        if (threshold == null || threshold < 0 || threshold > 1) {
            throw new IllegalArgumentException("Le seuil doit être entre 0 et 1.");
        }
    }

    private double getDefaultThreshold(String agentName) {
        return switch (agentName) {
            case "AGENT_ROUTEUR" -> 0.70;
            case "AGENT_VALIDATION" -> 0.60;
            case "AGENT_ESTIMATEUR" -> 0.70;
            default -> 0.70;
        };
    }
}