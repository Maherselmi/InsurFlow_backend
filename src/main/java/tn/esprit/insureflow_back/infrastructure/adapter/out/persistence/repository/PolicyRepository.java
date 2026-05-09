package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.insureflow_back.domain.model.Policy;

import java.util.List;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
    List<Policy> findByClient_Id(Long clientId);

}
