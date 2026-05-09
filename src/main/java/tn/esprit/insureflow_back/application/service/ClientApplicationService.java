package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.domain.model.Client;
import tn.esprit.insureflow_back.domain.port.in.ClientUseCase;
import tn.esprit.insureflow_back.domain.port.out.ClientRepositoryPort;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientApplicationService implements ClientUseCase {

    private final ClientRepositoryPort clientRepositoryPort;

    @Override
    public Client createClient(Client client) {
        return clientRepositoryPort.save(client);
    }

    @Override
    public Client updateClient(Long id, Client client) {
        Client existingClient = getClientById(id);

        existingClient.setFirstName(client.getFirstName());
        existingClient.setLastName(client.getLastName());
        existingClient.setEmail(client.getEmail());
        existingClient.setPhone(client.getPhone());

        return clientRepositoryPort.save(existingClient);
    }

    @Override
    public Client getClientById(Long id) {
        return clientRepositoryPort.findById(id)
                .orElseThrow(() -> new RuntimeException("Client introuvable : " + id));
    }

    @Override
    public List<Client> getAllClients() {
        return clientRepositoryPort.findAll();
    }

    @Override
    public void deleteClient(Long id) {
        clientRepositoryPort.deleteById(id);
    }
}