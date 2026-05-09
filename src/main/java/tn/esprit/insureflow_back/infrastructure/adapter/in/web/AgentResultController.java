package tn.esprit.insureflow_back.infrastructure.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.insureflow_back.domain.model.AgentResult;
import tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository.AgentResultRepository;

import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/agent-results")
@RequiredArgsConstructor
public class AgentResultController {

    private final AgentResultRepository agentResultRepository;

    @GetMapping
    public ResponseEntity<List<AgentResult>> getAll() {
        return ResponseEntity.ok(agentResultRepository.findAll());
    }

    @GetMapping("/claim/{claimId}")
    public ResponseEntity<List<AgentResult>> getByClaim(@PathVariable Long claimId) {
        return ResponseEntity.ok(
                agentResultRepository.findAll().stream()
                        .filter(r -> r.getClaim() != null &&
                                r.getClaim().getId().equals(claimId))
                        .toList()
        );
    }
}