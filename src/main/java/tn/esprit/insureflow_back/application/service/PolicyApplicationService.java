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

/**
 * Service applicatif responsable de la gestion des polices d’assurance.
 * Il permet de créer, modifier, consulter et supprimer des contrats.
 */
@Service
@RequiredArgsConstructor
public class PolicyApplicationService implements PolicyUseCase {

    /*
     * Ports utilisés pour accéder aux polices d’assurance et aux clients.
     */
    private final PolicyRepositoryPort policyRepositoryPort;
    private final ClientRepositoryPort clientRepositoryPort;

    /*
     * Crée une nouvelle police d’assurance.
     * Le client associé est vérifié avant la sauvegarde.
     */
    @Override
    public Policy createPolicy(Policy policy) {

        /*
         * Vérifie que les données obligatoires de la police sont présentes.
         */
        validatePolicy(policy);

        Long clientId = policy.getClient().getId();

        /*
         * Recherche du client propriétaire de la police.
         */
        Client client = clientRepositoryPort.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        /*
         * Association du client existant à la police.
         */
        policy.setClient(client);

        /*
         * Normalise les champs texte avant la sauvegarde.
         */
        normalizePolicy(policy);

        return policyRepositoryPort.save(policy);
    }

    /*
     * Met à jour une police d’assurance existante.
     */
    @Override
    public Policy updatePolicy(Long id, Policy policy) {

        /*
         * Recherche de la police existante avant modification.
         */
        Policy existing = getPolicyById(id);

        /*
         * Mise à jour des champs modifiables.
         */
        existing.setType(policy.getType());
        existing.setFormule(policy.getFormule());
        existing.setPolicyNumber(policy.getPolicyNumber());
        existing.setProductCode(policy.getProductCode());
        existing.setStartDate(policy.getStartDate());
        existing.setEndDate(policy.getEndDate());

        /*
         * Normalisation des valeurs avant sauvegarde.
         */
        normalizePolicy(existing);

        return policyRepositoryPort.save(existing);
    }

    /*
     * Récupère une police d’assurance par son identifiant.
     */
    @Override
    public Policy getPolicyById(Long id) {
        return policyRepositoryPort.findById(id)
                .orElseThrow(() -> new RuntimeException("Policy not found"));
    }

    /*
     * Récupère toutes les polices d’assurance.
     */
    @Override
    public List<Policy> getAllPolicies() {
        return policyRepositoryPort.findAll();
    }

    /*
     * Supprime une police d’assurance par son identifiant.
     */
    @Override
    public void deletePolicy(Long id) {
        policyRepositoryPort.deleteById(id);
    }

    /*
     * Récupère toutes les polices liées à un client donné.
     * L’existence du client est vérifiée avant la recherche.
     */
    public List<Policy> getPoliciesByClientId(Long clientId) {

        clientRepositoryPort.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        return policyRepositoryPort.findByClientId(clientId);
    }

    /*
     * Vérifie que la police contient toutes les données obligatoires.
     */
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

        /*
         * Vérifie que la date de début ne dépasse pas la date de fin.
         */
        if (policy.getStartDate().isAfter(policy.getEndDate())) {
            throw new RuntimeException("Invalid dates");
        }
    }

    /*
     * Normalise les champs texte de la police.
     * Cela évite les différences entre majuscules, minuscules et espaces.
     */
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