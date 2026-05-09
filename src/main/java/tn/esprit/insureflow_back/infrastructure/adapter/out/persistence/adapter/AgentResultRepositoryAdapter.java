package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.domain.model.AgentResult;
import tn.esprit.insureflow_back.domain.port.out.AgentResultRepositoryPort;
import tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository.AgentResultRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AgentResultRepositoryAdapter implements AgentResultRepositoryPort {

    private final AgentResultRepository agentResultRepository;

    @Override
    public AgentResult save(AgentResult result) {
        return agentResultRepository.save(result);
    }

    @Override
    public Optional<AgentResult> findById(Long id) {
        return agentResultRepository.findById(id);
    }

    @Override
    public List<AgentResult> findAll() {
        return agentResultRepository.findAll();
    }

    @Override
    public List<AgentResult> findByClaimId(Long claimId) {
        return agentResultRepository.findAll()
                .stream()
                .filter(result -> result.getClaim() != null
                        && result.getClaim().getId() != null
                        && result.getClaim().getId().equals(claimId))
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        agentResultRepository.deleteById(id);
    }
}