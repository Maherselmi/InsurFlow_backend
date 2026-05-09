package tn.esprit.insureflow_back.domain.port.out;

import tn.esprit.insureflow_back.domain.model.Client;

import java.util.List;
import java.util.Optional;

public interface ClientRepositoryPort {

    Client save(Client client);

    Optional<Client> findById(Long id);

    Optional<Client> findByEmail(String email);

    List<Client> findAll();

    void deleteById(Long id);
}