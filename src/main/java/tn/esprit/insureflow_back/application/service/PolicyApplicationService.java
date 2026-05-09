package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.domain.model.Client;
import tn.esprit.insureflow_back.domain.model.Policy;
import tn.esprit.insureflow_back.domain.port.in.PolicyUseCase;
import tn.esprit.insureflow_back.domain.port.out.ClientRepositoryPort;
import tn.esprit.insureflow_back.domain.port.out.PolicyRepositoryPort;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PolicyApplicationService implements PolicyUseCase {

    private final PolicyRepositoryPort policyRepositoryPort;
    private final ClientRepositoryPort clientRepositoryPort;

    @Override
    public Policy createPolicy(Policy policy) {

        validatePolicy(policy);

        Long clientId = policy.getClient().getId();

        Client client = clientRepositoryPort.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        policy.setClient(client);

        normalizePolicy(policy);

        return policyRepositoryPort.save(policy);
    }

    @Override
    public Policy updatePolicy(Long id, Policy policy) {

        Policy existing = getPolicyById(id);

        existing.setType(policy.getType());
        existing.setFormule(policy.getFormule());
        existing.setPolicyNumber(policy.getPolicyNumber());
        existing.setProductCode(policy.getProductCode());
        existing.setStartDate(policy.getStartDate());
        existing.setEndDate(policy.getEndDate());

        normalizePolicy(existing);

        return policyRepositoryPort.save(existing);
    }

    @Override
    public Policy getPolicyById(Long id) {
        return policyRepositoryPort.findById(id)
                .orElseThrow(() -> new RuntimeException("Policy not found"));
    }

    @Override
    public List<Policy> getAllPolicies() {
        return policyRepositoryPort.findAll();
    }

    @Override
    public void deletePolicy(Long id) {
        policyRepositoryPort.deleteById(id);
    }

    public List<Policy> getPoliciesByClientId(Long clientId) {

        clientRepositoryPort.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        return policyRepositoryPort.findByClientId(clientId);
    }

    private void validatePolicy(Policy policy) {

        if (policy == null) {
            throw new RuntimeException("Policy is required");
        }

        if (policy.getClient() == null
                || policy.getClient().getId() == null) {
            throw new RuntimeException("Client is required");
        }

        if (policy.getPolicyNumber() == null
                || policy.getPolicyNumber().isBlank()) {
            throw new RuntimeException("Policy number is required");
        }

        if (policy.getType() == null
                || policy.getType().isBlank()) {
            throw new RuntimeException("Policy type is required");
        }

        if (policy.getStartDate() == null
                || policy.getEndDate() == null) {
            throw new RuntimeException("Dates are required");
        }

        if (policy.getStartDate().isAfter(policy.getEndDate())) {
            throw new RuntimeException("Invalid dates");
        }
    }

    private void normalizePolicy(Policy policy) {

        policy.setType(
                policy.getType().trim().toUpperCase(Locale.ROOT)
        );

        if (policy.getFormule() != null) {
            policy.setFormule(
                    policy.getFormule().trim().toUpperCase(Locale.ROOT)
            );
        }

        if (policy.getProductCode() != null) {
            policy.setProductCode(
                    policy.getProductCode().trim().toUpperCase(Locale.ROOT)
            );
        }

        policy.setPolicyNumber(
                policy.getPolicyNumber().trim().toUpperCase(Locale.ROOT)
        );
    }
}