package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.domain.model.Client;
import tn.esprit.insureflow_back.domain.port.in.ClientUseCase;
import tn.esprit.insureflow_back.domain.port.out.ClientRepositoryPort;

import java.util.List;

/**
 * Service applicatif responsable de la gestion des clients.
 * Il permet de créer, modifier, consulter et supprimer des clients.
 */
@Service
@RequiredArgsConstructor
public class ClientApplicationService implements ClientUseCase {

    /*
     * Port utilisé pour accéder aux données des clients.
     */
    private final ClientRepositoryPort clientRepositoryPort;

    /*
     * Crée un nouveau client.
     */
    @Override
    public Client createClient(Client client) {
        return clientRepositoryPort.save(client);
    }

    /*
     * Met à jour les informations d’un client existant.
     */
    @Override
    public Client updateClient(Long id, Client client) {
        /*
         * Recherche du client existant avant modification.
         */
        Client existingClient = getClientById(id);

        /*
         * Mise à jour des champs modifiables du client.
         */
        existingClient.setFirstName(client.getFirstName());
        existingClient.setLastName(client.getLastName());
        existingClient.setEmail(client.getEmail());
        existingClient.setPhone(client.getPhone());

        /*
         * Sauvegarde du client mis à jour.
         */
        return clientRepositoryPort.save(existingClient);
    }

    /*
     * Récupère un client par son identifiant.
     * Une exception est levée si le client n’existe pas.
     */
    @Override
    public Client getClientById(Long id) {
        return clientRepositoryPort.findById(id)
                .orElseThrow(() -> new RuntimeException("Client introuvable : " + id));
    }

    /*
     * Récupère la liste de tous les clients.
     */
    @Override
    public List<Client> getAllClients() {
        return clientRepositoryPort.findAll();
    }

    /*
     * Supprime un client à partir de son identifiant.
     */
    @Override
    public void deleteClient(Long id) {
        clientRepositoryPort.deleteById(id);
    }
}