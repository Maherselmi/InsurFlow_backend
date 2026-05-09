package tn.esprit.insureflow_back.domain.port.in;

import tn.esprit.insureflow_back.application.dto.AuthResponse;
import tn.esprit.insureflow_back.application.dto.LoginRequest;
import tn.esprit.insureflow_back.application.dto.RegisterRequest;

public interface AuthUseCase {

    AuthResponse registerClient(RegisterRequest request);

    AuthResponse registerExpert(RegisterRequest request);

    AuthResponse registerAdmin(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}