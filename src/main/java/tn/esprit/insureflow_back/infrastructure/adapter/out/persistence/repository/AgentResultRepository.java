package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.insureflow_back.domain.model.AgentResult;

import java.util.List;

public interface AgentResultRepository extends JpaRepository<AgentResult, Long> {

    @Query("""
            SELECT ar
            FROM AgentResult ar
            WHERE ar.claim.id = :claimId
            ORDER BY ar.createdAt ASC
            """)
    List<AgentResult> findByClaimIdOrderByCreatedAtAsc(@Param("claimId") Long claimId);

}