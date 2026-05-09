package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository;

import tn.esprit.insureflow_back.domain.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
}