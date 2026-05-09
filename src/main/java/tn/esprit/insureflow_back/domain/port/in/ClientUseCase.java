package tn.esprit.insureflow_back.domain.port.in;

import tn.esprit.insureflow_back.domain.model.Client;

import java.util.List;

public interface ClientUseCase {

    Client createClient(Client client);

    Client updateClient(Long id, Client client);

    Client getClientById(Long id);

    List<Client> getAllClients();

    void deleteClient(Long id);
}