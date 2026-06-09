package tn.esprit.insureflow_back.infrastructure.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class OllamaConfig {

    @Value("${openrouter.base-url}")
    private String openRouterBaseUrl;

    @Value("${openrouter.api-key}")
    private String openRouterApiKey;

    @Value("${openrouter.chat-model}")
    private String chatModelName;

    @Value("${openrouter.vision-model}")
    private String visionModelName;

    @Value("${openrouter.chat-max-tokens}")
    private Integer chatMaxTokens;

    @Value("${openrouter.vision-max-tokens}")
    private Integer visionMaxTokens;

    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${ollama.embedding-model}")
    private String embeddingModelName;

    @Bean(name = "chatLanguageModel")
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(openRouterBaseUrl)
                .apiKey(openRouterApiKey)
                .modelName(chatModelName)
                .temperature(0.0)
                .maxTokens(chatMaxTokens)
                .timeout(Duration.ofSeconds(300))
                .build();
    }

    @Bean(name = "embeddingModel")
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(embeddingModelName)
                .build();
    }

    @Bean(name = "visionLanguageModel")
    public ChatLanguageModel visionLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(openRouterBaseUrl)
                .apiKey(openRouterApiKey)
                .modelName(visionModelName)
                .temperature(0.1)
                .maxTokens(visionMaxTokens)
                .timeout(Duration.ofMinutes(10))
                .build();
    }
}