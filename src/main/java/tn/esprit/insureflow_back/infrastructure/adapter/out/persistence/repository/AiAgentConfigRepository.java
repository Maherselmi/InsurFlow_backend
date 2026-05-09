package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.insureflow_back.domain.model.AiAgentConfig;

import java.util.Optional;

public interface AiAgentConfigRepository extends JpaRepository<AiAgentConfig, Long> {
    Optional<AiAgentConfig> findByAgentName(String agentName);
}