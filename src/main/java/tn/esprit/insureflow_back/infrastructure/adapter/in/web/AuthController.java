package tn.esprit.insureflow_back.infrastructure.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.insureflow_back.application.dto.AuthResponse;
import tn.esprit.insureflow_back.application.dto.LoginRequest;
import tn.esprit.insureflow_back.application.dto.RegisterRequest;
import tn.esprit.insureflow_back.application.service.AuthApplicationService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {

    private final AuthApplicationService authService;

    @PostMapping("/register/client")
    public ResponseEntity<AuthResponse> registerClient(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.registerClient(request));
    }

    @PostMapping("/register/expert")
    public ResponseEntity<AuthResponse> registerExpert(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.registerExpert(request));
    }

    @PostMapping("/register/admin")
    public ResponseEntity<AuthResponse> registerAdmin(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.registerAdmin(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}