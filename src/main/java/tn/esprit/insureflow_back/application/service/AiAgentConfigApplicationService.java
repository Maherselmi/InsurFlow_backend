package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.domain.model.AiAgentConfig;
import tn.esprit.insureflow_back.domain.port.in.AiAgentConfigUseCase;
import tn.esprit.insureflow_back.domain.port.out.AiAgentConfigRepositoryPort;

import java.util.List;

/**
 * Service applicatif pour gérer la configuration des agents IA.
 * Il permet de créer, modifier, consulter et supprimer les seuils de confiance.
 */
@Service
@RequiredArgsConstructor
public class AiAgentConfigApplicationService implements AiAgentConfigUseCase {

    /*
     * Port de sortie utilisé pour accéder aux configurations des agents.
     * Le service ne dépend pas directement de la base de données.
     */
    private final AiAgentConfigRepositoryPort aiAgentConfigRepositoryPort;

    /*
     * Crée une nouvelle configuration pour un agent IA.
     */
    @Override
    public AiAgentConfig createConfig(AiAgentConfig config) {
        return aiAgentConfigRepositoryPort.save(config);
    }

    /*
     * Met à jour une configuration existante à partir de son identifiant.
     */
    @Override
    public AiAgentConfig updateConfig(Long id, AiAgentConfig config) {
        AiAgentConfig existing = getConfigById(id);

        existing.setAgentName(config.getAgentName());
        existing.setConfidenceThreshold(config.getConfidenceThreshold());

        validateThreshold(existing.getConfidenceThreshold());

        return aiAgentConfigRepositoryPort.save(existing);
    }

    /*
     * Met à jour uniquement le seuil de confiance d’un agent.
     * Si la configuration n’existe pas, elle est créée automatiquement.
     */
    public AiAgentConfig updateThreshold(String agentName, Double threshold) {
        validateThreshold(threshold);

        AiAgentConfig config = aiAgentConfigRepositoryPort.findByAgentName(agentName)
                .orElse(
                        AiAgentConfig.builder()
                                .agentName(agentName)
                                .confidenceThreshold(threshold)
                                .build()
                );

        config.setConfidenceThreshold(threshold);

        return aiAgentConfigRepositoryPort.save(config);
    }

    /*
     * Récupère le seuil de confiance d’un agent.
     * Si aucune configuration n’existe, un seuil par défaut est retourné.
     */
    public double getThreshold(String agentName) {
        return aiAgentConfigRepositoryPort.findByAgentName(agentName)
                .map(AiAgentConfig::getConfidenceThreshold)
                .orElseGet(() -> getDefaultThreshold(agentName));
    }

    /*
     * Recherche une configuration par son identifiant.
     * Une exception est levée si elle n’existe pas.
     */
    @Override
    public AiAgentConfig getConfigById(Long id) {
        return aiAgentConfigRepositoryPort.findById(id)
                .orElseThrow(() -> new RuntimeException("Configuration agent introuvable"));
    }

    /*
     * Recherche une configuration par le nom de l’agent.
     * Une exception est levée si elle n’existe pas.
     */
    @Override
    public AiAgentConfig getConfigByAgentName(String agentName) {
        return aiAgentConfigRepositoryPort.findByAgentName(agentName)
                .orElseThrow(() -> new RuntimeException("Configuration agent introuvable"));
    }

    /*
     * Retourne toutes les configurations des agents IA.
     */
    @Override
    public List<AiAgentConfig> getAllConfigs() {
        return aiAgentConfigRepositoryPort.findAll();
    }

    /*
     * Supprime une configuration à partir de son identifiant.
     */
    @Override
    public void deleteConfig(Long id) {
        aiAgentConfigRepositoryPort.deleteById(id);
    }

    /*
     * Vérifie que le seuil est valide.
     * Le seuil doit être compris entre 0 et 1.
     */
    private void validateThreshold(Double threshold) {
        if (threshold == null || threshold < 0 || threshold > 1) {
            throw new IllegalArgumentException("Le seuil doit être entre 0 et 1.");
        }
    }

    /*
     * Définit les seuils par défaut selon le type d’agent.
     */
    private double getDefaultThreshold(String agentName) {
        return switch (agentName) {
            case "AGENT_ROUTEUR" -> 0.70;
            case "AGENT_VALIDATION" -> 0.60;
            case "AGENT_ESTIMATEUR" -> 0.70;
            default -> 0.70;
        };
    }
}