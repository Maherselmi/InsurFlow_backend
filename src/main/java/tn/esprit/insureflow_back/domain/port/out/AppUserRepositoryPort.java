package tn.esprit.insureflow_back.domain.port.out;

import tn.esprit.insureflow_back.domain.model.AppUser;

import java.util.List;
import java.util.Optional;

public interface AppUserRepositoryPort {

    AppUser save(AppUser user);

    Optional<AppUser> findById(Long id);

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    List<AppUser> findAll();

    void deleteById(Long id);
}