package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.domain.model.AiAgentConfig;
import tn.esprit.insureflow_back.domain.port.out.AiAgentConfigRepositoryPort;
import tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository.AiAgentConfigRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AiAgentConfigRepositoryAdapter implements AiAgentConfigRepositoryPort {

    private final AiAgentConfigRepository aiAgentConfigRepository;

    @Override
    public AiAgentConfig save(AiAgentConfig config) {
        return aiAgentConfigRepository.save(config);
    }

    @Override
    public Optional<AiAgentConfig> findById(Long id) {
        return aiAgentConfigRepository.findById(id);
    }

    @Override
    public Optional<AiAgentConfig> findByAgentName(String agentName) {
        return aiAgentConfigRepository.findByAgentName(agentName);
    }

    @Override
    public List<AiAgentConfig> findAll() {
        return aiAgentConfigRepository.findAll();
    }

    @Override
    public void deleteById(Long id) {
        aiAgentConfigRepository.deleteById(id);
    }
}