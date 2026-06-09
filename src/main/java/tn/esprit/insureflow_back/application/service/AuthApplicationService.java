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

/**
 * Service applicatif responsable de l’authentification.
 * Il gère l’inscription des utilisateurs et la connexion avec JWT.
 */
@Service
@RequiredArgsConstructor
public class AuthApplicationService implements AuthUseCase {

    /*
     * Repository utilisé pour gérer les utilisateurs de l’application.
     */
    private final AppUserRepositoryPort appUserRepositoryPort;

    /*
     * Repository utilisé pour gérer les informations liées aux clients.
     */
    private final ClientRepositoryPort clientRepositoryPort;

    /*
     * Encodeur utilisé pour sécuriser les mots de passe avant stockage.
     */
    private final PasswordEncoder passwordEncoder;

    /*
     * Gestionnaire Spring Security utilisé pour vérifier les identifiants.
     */
    private final AuthenticationManager authenticationManager;

    /*
     * Service utilisé pour générer les tokens JWT après authentification.
     */
    private final JwtService jwtService;

    /*
     * Inscrit un nouveau client dans l’application.
     * Un compte utilisateur est créé avec le rôle CLIENT.
     */
    @Override
    public AuthResponse registerClient(RegisterRequest request) {
        if (appUserRepositoryPort.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email déjà utilisé");
        }

        /*
         * Création du compte utilisateur avec mot de passe encodé.
         */
        AppUser user = AppUser.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_CLIENT)
                .enabled(true)
                .build();

        /*
         * Création du profil client associé au compte utilisateur.
         */
        Client client = new Client();
        client.setFirstName(request.getFirstName());
        client.setLastName(request.getLastName());
        client.setEmail(request.getEmail());
        client.setPhone(request.getPhone());
        client.setUser(user);

        /*
         * Association bidirectionnelle entre l’utilisateur et le client.
         */
        user.setClient(client);

        /*
         * Sauvegarde de l’utilisateur.
         * Le client sera aussi sauvegardé si la relation est configurée avec cascade.
         */
        appUserRepositoryPort.save(user);

        /*
         * Génération du token JWT après inscription.
         */
        String token = jwtService.generateToken(user);

        return new AuthResponse(token, user.getEmail(), user.getRole().name());
    }

    /*
     * Inscrit un expert avec le rôle ROLE_EXPERT.
     */
    @Override
    public AuthResponse registerExpert(RegisterRequest request) {
        return registerUser(request, Role.ROLE_EXPERT);
    }

    /*
     * Inscrit un administrateur avec le rôle ROLE_ADMIN.
     */
    @Override
    public AuthResponse registerAdmin(RegisterRequest request) {
        return registerUser(request, Role.ROLE_ADMIN);
    }

    /*
     * Authentifie un utilisateur avec email et mot de passe.
     * Si les identifiants sont valides, un token JWT est généré.
     */
    @Override
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        /*
         * Récupération de l’utilisateur après authentification réussie.
         */
        AppUser user = appUserRepositoryPort.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        /*
         * Génération du token JWT pour l’utilisateur connecté.
         */
        String token = jwtService.generateToken(user);

        return new AuthResponse(token, user.getEmail(), user.getRole().name());
    }

    /*
     * Méthode privée commune pour inscrire un expert ou un administrateur.
     * Elle évite de dupliquer la logique de création d’utilisateur.
     */
    private AuthResponse registerUser(RegisterRequest request, Role role) {
        if (appUserRepositoryPort.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email déjà utilisé");
        }

        /*
         * Création d’un utilisateur avec le rôle passé en paramètre.
         */
        AppUser user = AppUser.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .enabled(true)
                .build();

        /*
         * Sauvegarde du nouvel utilisateur.
         */
        appUserRepositoryPort.save(user);

        /*
         * Génération du token JWT après inscription.
         */
        String token = jwtService.generateToken(user);

        return new AuthResponse(token, user.getEmail(), user.getRole().name());
    }
}