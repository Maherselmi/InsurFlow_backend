package tn.esprit.insureflow_back.infrastructure.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.insureflow_back.application.dto.AiAgentConfigRequest;
import tn.esprit.insureflow_back.application.service.AiAgentConfigApplicationService;
import tn.esprit.insureflow_back.domain.model.AiAgentConfig;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai-config")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class AiAgentConfigController {

    private final AiAgentConfigApplicationService service;

    @GetMapping
    public ResponseEntity<List<AiAgentConfig>> getAllConfigs() {
        return ResponseEntity.ok(service.getAllConfigs());
    }

    @PutMapping
    public ResponseEntity<AiAgentConfig> updateThreshold(@RequestBody AiAgentConfigRequest request) {
        AiAgentConfig updated = service.updateThreshold(
                request.getAgentName(),
                request.getConfidenceThreshold()
        );
        return ResponseEntity.ok(updated);
    }
}