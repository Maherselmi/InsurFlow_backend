package tn.esprit.insureflow_back.infrastructure.adapter.in.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.insureflow_back.infrastructure.adapter.out.ai.AgentRouteur;
import tn.esprit.insureflow_back.domain.model.AgentResult;
import tn.esprit.insureflow_back.domain.model.Claim;
import tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository.AgentResultRepository;
import tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository.ClaimRepository;

@Slf4j
@RestController
@RequestMapping("/api/orchestrator")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class OrchestratorController {

    private final AgentRouteur    agentRouteur;
    private final ClaimRepository claimRepository;
    private final AgentResultRepository agentResultRepository;

    // ✅ Test AgentRouteur sur un claim existant
    @PostMapping("/classify/{claimId}")
    public ResponseEntity<AgentResult> classifyClaim(@PathVariable Long claimId) {

        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim introuvable : " + claimId));

        AgentResult result = agentRouteur.classifier(claim);

        log.info("✅ Classification terminée: {}", result.getConclusion());
        // 🔥 SAUVEGARDE EN BASE
        result.setClaim(claim); // IMPORTANT
        result = agentResultRepository.save(result);
        return ResponseEntity.ok(result);
    }
}