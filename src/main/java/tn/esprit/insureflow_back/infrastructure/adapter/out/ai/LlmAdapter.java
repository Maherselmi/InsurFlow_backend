package tn.esprit.insureflow_back.infrastructure.adapter.out.ai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.domain.port.out.LlmPort;

@Slf4j
@Component
public class LlmAdapter implements LlmPort {

    private final ChatLanguageModel chatLanguageModel;

    public LlmAdapter(@Qualifier("chatLanguageModel") ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    @Override
    public String generateResponse(String prompt) {
        try {
            log.info("Envoi prompt au LLM ({} chars)", prompt.length());

            String response = chatLanguageModel.generate(prompt);

            log.info("Réponse LLM reçue ({} chars)", response.length());

            return response;

        } catch (Exception e) {
            log.error("Erreur LLM : {}", e.getMessage());
            throw new RuntimeException("Erreur communication LLM : " + e.getMessage(), e);
        }
    }

    @Override
    public String analyzeClaim(String claimContext) {
        return generateResponse(claimContext);
    }

    @Override
    public String validateClaim(String claimContext) {
        return generateResponse(claimContext);
    }

    @Override
    public String estimateClaim(String claimContext) {
        return generateResponse(claimContext);
    }
}