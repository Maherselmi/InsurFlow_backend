package tn.esprit.insureflow_back.infrastructure.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.insureflow_back.domain.model.Claim;
import tn.esprit.insureflow_back.domain.port.in.ClaimUseCase;
import tn.esprit.insureflow_back.domain.port.in.HumanValidationUseCase;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimUseCase claimUseCase;
    private final HumanValidationUseCase humanValidationUseCase;

    @PostMapping
    public Claim createClaim(@RequestBody Claim claim) {
        return claimUseCase.createClaim(claim);
    }

    @GetMapping
    public List<Claim> getAllClaims() {
        return claimUseCase.getAllClaims();
    }

    @GetMapping("/{id}")
    public Claim getClaim(@PathVariable Long id) {
        return claimUseCase.getClaimById(id);
    }

    @GetMapping("/pending-validation")
    public ResponseEntity<List<Claim>> getPendingValidation() {
        return ResponseEntity.ok(claimUseCase.getPendingValidation());
    }

    @GetMapping("/{id}/review")
    public ResponseEntity<Map<String, Object>> getClaimForReview(@PathVariable Long id) {
        Claim claim = claimUseCase.getClaimById(id);

        return ResponseEntity.ok(Map.of(
                "id", claim.getId(),
                "description", claim.getDescription(),
                "status", claim.getStatus(),
                "incidentDate", claim.getIncidentDate(),
                "createdAt", claim.getCreatedAt(),
                "aiReport", claim.getAiReport() != null ? claim.getAiReport() : "Aucun rapport disponible"
        ));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveClaim(
            @PathVariable Long id,
            @RequestBody HumanDecisionRequest request
    ) {
        Claim updated = humanValidationUseCase.approveClaim(
                id,
                request.comment(),
                request.finalEstimationMin(),
                request.finalEstimationMoyenne(),
                request.finalEstimationMax()
        );

        return ResponseEntity.ok(Map.of(
                "message", "Claim approuvé avec succès",
                "claimId", updated.getId(),
                "status", updated.getStatus().name()
        ));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectClaim(
            @PathVariable Long id,
            @RequestBody HumanDecisionRequest request
    ) {
        Claim updated = humanValidationUseCase.rejectClaim(id, request.comment());

        return ResponseEntity.ok(Map.of(
                "message", "Claim rejeté avec succès",
                "claimId", updated.getId(),
                "status", updated.getStatus().name()
        ));
    }

    @GetMapping("/{id}/reports")
    public ResponseEntity<Map<String, Object>> getClaimReports(@PathVariable Long id) {
        Claim claim = claimUseCase.getClaimReports(id);

        return ResponseEntity.ok(Map.of(
                "claimId", claim.getId(),
                "description", claim.getDescription(),
                "status", claim.getStatus() != null ? claim.getStatus().name() : "INCONNU",
                "incidentDate", claim.getIncidentDate(),
                "aiReport", claim.getAiReport() != null ? claim.getAiReport() : "Aucun rapport IA expert disponible",
                "clientReport", claim.getClientReport() != null ? claim.getClientReport() : "Aucun rapport client disponible"
        ));
    }

    public record HumanDecisionRequest(
            String comment,
            Double finalEstimationMin,
            Double finalEstimationMoyenne,
            Double finalEstimationMax
    ) {}
}