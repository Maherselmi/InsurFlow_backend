package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.application.dto.AuthResponse;
import tn.esprit.insureflow_back.application.dto.LoginRequest;
import tn.esprit.insureflow_back.application.dto.RegisterRequest;
import tn.esprit.insureflow_back.domain.enums.Role;
import tn.esprit.insureflow_back.domain.model.AppUser;
import tn.esprit.insureflow_back.domain.model.Client;
import tn.esprit.insureflow_back.domain.port.in.AuthUseCase;
import tn.esprit.insureflow_back.domain.port.out.AppUserRepositoryPort;
import tn.esprit.insureflow_back.domain.port.out.ClientRepositoryPort;
import tn.esprit.insureflow_back.infrastructure.Security.JwtService;

@Service
@RequiredArgsConstructor
public class AuthApplicationService implements AuthUseCase {

    private final AppUserRepositoryPort appUserRepositoryPort;
    private final ClientRepositoryPort clientRepositoryPort;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    public AuthResponse registerClient(RegisterRequest request) {
        if (appUserRepositoryPort.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email déjà utilisé");
        }

        AppUser user = AppUser.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_CLIENT)
                .enabled(true)
                .build();

        Client client = new Client();
        client.setFirstName(request.getFirstName());
        client.setLastName(request.getLastName());
        client.setEmail(request.getEmail());
        client.setPhone(request.getPhone());
        client.setUser(user);

        user.setClient(client);

        appUserRepositoryPort.save(user);

        String token = jwtService.generateToken(user);

        return new AuthResponse(token, user.getEmail(), user.getRole().name());
    }

    @Override
    public AuthResponse registerExpert(RegisterRequest request) {
        return registerUser(request, Role.ROLE_EXPERT);
    }

    @Override
    public AuthResponse registerAdmin(RegisterRequest request) {
        return registerUser(request, Role.ROLE_ADMIN);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        AppUser user = appUserRepositoryPort.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        String token = jwtService.generateToken(user);

        return new AuthResponse(token, user.getEmail(), user.getRole().name());
    }

    private AuthResponse registerUser(RegisterRequest request, Role role) {
        if (appUserRepositoryPort.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email déjà utilisé");
        }

        AppUser user = AppUser.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .enabled(true)
                .build();

        appUserRepositoryPort.save(user);

        String token = jwtService.generateToken(user);

        return new AuthResponse(token, user.getEmail(), user.getRole().name());
    }
}