package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.domain.model.Policy;
import tn.esprit.insureflow_back.domain.port.out.PolicyRepositoryPort;
import tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository.PolicyRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PolicyRepositoryAdapter implements PolicyRepositoryPort {

    private final PolicyRepository policyRepository;

    @Override
    public Policy save(Policy policy) {
        return policyRepository.save(policy);
    }

    @Override
    public Optional<Policy> findById(Long id) {
        return policyRepository.findById(id);
    }

    @Override
    public Optional<Policy> findByPolicyNumber(String policyNumber) {
        return policyRepository.findAll()
                .stream()
                .filter(policy -> policy.getPolicyNumber() != null
                        && policy.getPolicyNumber().equalsIgnoreCase(policyNumber))
                .findFirst();
    }

    @Override
    public List<Policy> findAll() {
        return policyRepository.findAll();
    }

    @Override
    public List<Policy> findByClientId(Long clientId) {
        return policyRepository.findByClient_Id(clientId);
    }

    @Override
    public void deleteById(Long id) {
        policyRepository.deleteById(id);
    }
}