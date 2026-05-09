package tn.esprit.insureflow_back.domain.port.out;

import tn.esprit.insureflow_back.domain.model.Policy;

import java.util.List;
import java.util.Optional;

public interface PolicyRepositoryPort {

    Policy save(Policy policy);

    Optional<Policy> findById(Long id);

    Optional<Policy> findByPolicyNumber(String policyNumber);

    List<Policy> findAll();

    List<Policy> findByClientId(Long clientId);

    void deleteById(Long id);
}