package tn.esprit.insureflow_back.application.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

@Service
public class AssistantDatasetCsvService {

    @Value("classpath:datasets/assistant_client_dataset.csv")
    private Resource datasetResource;

    private final Map<String, Map<String, List<String>>> intents = new HashMap<>();
    private final Map<String, List<String>> claimTypes = new LinkedHashMap<>();
    private final Map<String, List<String>> statuses = new LinkedHashMap<>();
    private final Map<String, String> prompts = new HashMap<>();
    private final Map<String, String> messages = new HashMap<>();

    @PostConstruct
    public void loadDataset() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(datasetResource.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                if (line.isBlank()) {
                    continue;
                }

                String[] parts = line.split(";", 4);

                if (parts.length < 4) {
                    continue;
                }

                String domain = parts[0].trim();
                String key = parts[1].trim();
                String group = parts[2].trim();
                String value = parts[3].trim().replace("\\n", "\n");

                switch (domain) {
                    case "INTENT" -> intents
                            .computeIfAbsent(key, k -> new LinkedHashMap<>())
                            .computeIfAbsent(group, g -> new ArrayList<>())
                            .add(value);

                    case "CLAIM_TYPE" -> claimTypes
                            .computeIfAbsent(key, k -> new ArrayList<>())
                            .add(value);

                    case "STATUS" -> statuses
                            .computeIfAbsent(key, k -> new ArrayList<>())
                            .add(value);

                    case "PROMPT" -> prompts.put(key, value);

                    case "MESSAGE" -> messages.put(key, value);

                    default -> {
                        // Ignore unknown domain
                    }
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur chargement assistant_client_dataset.csv", e);
        }
    }

    public boolean matchesIntent(String intentName, String message) {
        Map<String, List<String>> groups = intents.get(intentName);

        if (groups == null || groups.isEmpty()) {
            return false;
        }

        String normalizedMessage = normalizeText(message);

        if (groups.containsKey("ANY")) {
            return containsAny(normalizedMessage, groups.get("ANY"));
        }

        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            if (!containsAny(normalizedMessage, entry.getValue())) {
                return false;
            }
        }

        return true;
    }

    public String detectClaimType(String message) {
        String normalizedMessage = normalizeText(message);

        for (Map.Entry<String, List<String>> entry : claimTypes.entrySet()) {
            if (containsAny(normalizedMessage, entry.getValue())) {
                return entry.getKey();
            }
        }

        return null;
    }

    public String detectStatus(String message) {
        String normalizedMessage = normalizeText(message);

        for (Map.Entry<String, List<String>> entry : statuses.entrySet()) {
            if (containsAny(normalizedMessage, entry.getValue())) {
                return entry.getKey();
            }
        }

        return null;
    }

    public String getPrompt(String key) {
        return prompts.getOrDefault(key, "");
    }

    public String getMessage(String key, String defaultValue) {
        return messages.getOrDefault(key, defaultValue);
    }

    private boolean containsAny(String normalizedMessage, List<String> keywords) {
        if (normalizedMessage == null || keywords == null) {
            return false;
        }

        for (String keyword : keywords) {
            if (normalizedMessage.contains(normalizeText(keyword))) {
                return true;
            }
        }

        return false;
    }

    public String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return normalized
                .toLowerCase(Locale.ROOT)
                .replace("’", "'")
                .replace("'", " ")
                .replace("-", " ")
                .replace("_", " ")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}