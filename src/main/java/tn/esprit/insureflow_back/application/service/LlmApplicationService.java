package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.domain.port.out.LlmPort;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmApplicationService {

    private final LlmPort llmPort;

    public String genererReponse(String promptText) {
        if (promptText == null || promptText.isBlank()) {
            throw new RuntimeException("Prompt vide");
        }

        log.info("Envoi prompt au LLM ({} chars)", promptText.length());

        String response = llmPort.generateResponse(promptText);

        log.info("Réponse LLM reçue ({} chars)", response.length());

        return response;
    }
}