package tn.esprit.insureflow_back.domain.port.in;

import tn.esprit.insureflow_back.domain.model.Policy;

import java.util.List;

public interface PolicyUseCase {

    Policy createPolicy(Policy policy);

    Policy updatePolicy(Long id, Policy policy);

    Policy getPolicyById(Long id);

    List<Policy> getAllPolicies();

    void deletePolicy(Long id);
    List<Policy> getPoliciesByClientId(Long clientId);
}