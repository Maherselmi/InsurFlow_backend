package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.domain.model.AppUser;
import tn.esprit.insureflow_back.domain.port.out.AppUserRepositoryPort;
import tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository.AppUserRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AppUserRepositoryAdapter implements AppUserRepositoryPort {

    private final AppUserRepository appUserRepository;

    @Override
    public AppUser save(AppUser user) {
        return appUserRepository.save(user);
    }

    @Override
    public Optional<AppUser> findById(Long id) {
        return appUserRepository.findById(id);
    }

    @Override
    public Optional<AppUser> findByEmail(String email) {
        return appUserRepository.findByEmail(email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return appUserRepository.existsByEmail(email);
    }

    @Override
    public List<AppUser> findAll() {
        return appUserRepository.findAll();
    }

    @Override
    public void deleteById(Long id) {
        appUserRepository.deleteById(id);
    }
}