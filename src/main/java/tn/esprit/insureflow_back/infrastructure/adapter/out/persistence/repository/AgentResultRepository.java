package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.insureflow_back.domain.model.AgentResult;

public interface AgentResultRepository extends JpaRepository<AgentResult, Long> {

}