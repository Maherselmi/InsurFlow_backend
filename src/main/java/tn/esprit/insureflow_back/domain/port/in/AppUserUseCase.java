package tn.esprit.insureflow_back.domain.port.in;

import tn.esprit.insureflow_back.domain.model.AppUser;

import java.util.List;

public interface AppUserUseCase {

    AppUser createUser(AppUser user);

    AppUser getUserById(Long id);

    AppUser getUserByEmail(String email);

    List<AppUser> getAllUsers();

    void deleteUser(Long id);
}