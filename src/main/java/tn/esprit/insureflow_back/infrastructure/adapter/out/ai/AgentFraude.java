package tn.esprit.insureflow_back.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.application.dto.FraudAnalysisResult;
import tn.esprit.insureflow_back.domain.model.AgentResult;
import tn.esprit.insureflow_back.domain.model.Claim;
import tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository.AgentResultRepository;
import tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository.ClaimRepository;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentFraude {

    /*
     * Protection temporaire si la colonne PostgreSQL est encore VARCHAR(255).
     * Si tu modifies réellement la base en TEXT, tu peux augmenter cette valeur.
     */
    private static final int DB_SAFE_TEXT_LENGTH = 240;

    private final ChatLanguageModel chatLanguageModel;
    private final AgentResultRepository agentResultRepository;
    private final ClaimRepository claimRepository;
    private final ObjectMapper objectMapper;

    public AgentResult analyzeFraudRisk(Claim claim) {
        try {
            log.info("AgentFraude - début analyse fraude claim #{}", claim.getId());

            List<AgentResult> previousAgentResults =
                    agentResultRepository.findByClaimIdOrderByCreatedAtAsc(claim.getId());

            List<Claim> clientClaims = getClientClaims(claim);

            String prompt = buildFraudPrompt(claim, previousAgentResults, clientClaims);

            String rawResponse = chatLanguageModel.generate(prompt);

            FraudAnalysisResult fraudResult = parseFraudResponse(rawResponse);

            /*
             * Correction métier importante :
             * On limite les faux positifs.
             * Exemple : image différente de la description ou besoin de devis
             * ne signifie pas automatiquement fraude élevée.
             */
            fraudResult = correctFalsePositiveRisk(
                    fraudResult,
                    claim,
                    previousAgentResults,
                    clientClaims
            );

            AgentResult savedResult = AgentResult.builder()
                    .agentName("AgentFraude")
                    .conclusion(limitForDb(buildConclusion(fraudResult)))
                    .confidenceScore(safeScore(fraudResult.getScore()))
                    .needsHumanReview(fraudResult.isNeedsHumanReview())
                    .rawLlmResponse(limitForDb(buildCompactRawResponse(fraudResult)))
                    .claim(claim)
                    .build();

            AgentResult saved = agentResultRepository.save(savedResult);

            log.info(
                    "AgentFraude - analyse terminée claim #{} | risk={} | score={} | humanReview={}",
                    claim.getId(),
                    fraudResult.getFraudRisk(),
                    fraudResult.getScore(),
                    fraudResult.isNeedsHumanReview()
            );

            return saved;

        } catch (Exception e) {
            log.error("AgentFraude - erreur claim #{} : {}", claim.getId(), e.getMessage(), e);

            AgentResult fallback = AgentResult.builder()
                    .agentName("AgentFraude")
                    .conclusion("Risque vérification : MOYEN | score : 0.5")
                    .confidenceScore(0.5)
                    .needsHumanReview(true)
                    .rawLlmResponse(limitForDb("{\"fraudRisk\":\"MOYEN\",\"score\":0.5,\"needsHumanReview\":true,\"recommendation\":\"Validation humaine recommandée.\"}"))
                    .claim(claim)
                    .build();

            return agentResultRepository.save(fallback);
        }
    }

    private List<Claim> getClientClaims(Claim claim) {
        try {
            if (claim.getPolicy() == null || claim.getPolicy().getClient() == null) {
                return List.of();
            }

            Long clientId = claim.getPolicy().getClient().getId();

            return claimRepository.findByPolicy_Client_IdOrderByCreatedAtDesc(clientId);

        } catch (Exception e) {
            log.warn("AgentFraude - impossible de récupérer l'historique client : {}", e.getMessage());
            return List.of();
        }
    }

    private String buildFraudPrompt(
            Claim claim,
            List<AgentResult> previousAgentResults,
            List<Claim> clientClaims
    ) {
        String routeurResult = findAgentRaw(previousAgentResults, "routeur");
        String validateurResult = findAgentRaw(previousAgentResults, "validateur");
        String estimateurResult = findAgentRaw(previousAgentResults, "estimateur");

        long daysSincePolicyStart = calculateDaysSincePolicyStart(claim);
        int claimsCount = clientClaims != null ? clientClaims.size() : 0;
        int recentClaimsCount = countRecentClaims(clientClaims, claim);

        return """
                Tu es un agent IA spécialisé dans l'analyse du risque de fraude dans les dossiers de sinistre.

                Ton rôle n'est PAS d'accuser le client.
                Ton rôle est d'évaluer le niveau de risque du dossier et d'indiquer si une vérification humaine est nécessaire.

                IMPORTANT :
                - Une vérification humaine ne signifie pas automatiquement une fraude.
                - Un besoin de devis, d'expertise ou de photos supplémentaires ne signifie pas automatiquement une fraude.
                - Une différence partielle entre la description et l'image ne suffit pas pour classer le risque ELEVE.
                - Pour classer ELEVE, il faut plusieurs signaux forts en même temps.
                - Ne jamais écrire que le client est fraudeur.

                DOSSIER SINISTRE :
                - ID sinistre : %s
                - Date incident : %s
                - Description client : %s

                POLICE :
                - Numéro police : %s
                - Type police : %s
                - Date début police : %s
                - Date fin police : %s
                - Nombre de jours entre début police et sinistre : %s

                HISTORIQUE CLIENT :
                - Nombre total de sinistres client : %s
                - Nombre de sinistres récents sur 90 jours : %s

                RESULTAT AGENT ROUTEUR :
                %s

                RESULTAT AGENT VALIDATEUR :
                %s

                RESULTAT AGENT ESTIMATEUR :
                %s

                Critères de risque forts :
                - Sinistre déclaré très peu de temps après la souscription.
                - Plusieurs sinistres récents.
                - Contradiction majeure entre déclaration, documents et image.
                - Montant estimé clairement anormal par rapport aux dommages visibles.
                - Documents contradictoires ou fortement insuffisants.
                - Incohérence importante avec la police souscrite.

                RÈGLES ANTI-FAUX POSITIFS :
                - Si le sinistre est couvert, cohérent avec le type du contrat, et que l'historique client n'est pas suspect, le risque doit rester FAIBLE ou MOYEN.
                - Ne classe jamais ELEVE uniquement parce que l'image ne correspond pas parfaitement à la description.
                - Ne classe jamais ELEVE uniquement parce que l'agent estimateur demande une validation humaine.
                - Pour un dégât des eaux, la présence de dégâts sur plusieurs pièces peut être normale.
                - Pour un dégât des eaux, une photo montrant une fuite, un sol mouillé ou un meuble endommagé reste cohérente avec le sinistre, même si la pièce exacte n'est pas identique.
                - Si une photo supplémentaire ou un devis est nécessaire, utilise plutôt MOYEN, pas ELEVE.

                Réponds uniquement en JSON valide, sans markdown, sans texte avant ou après.

                Format obligatoire :
                {
                  "fraudRisk": "FAIBLE | MOYEN | ELEVE",
                  "score": 0.0,
                  "reasons": [
                    "raison courte 1",
                    "raison courte 2"
                  ],
                  "needsHumanReview": true,
                  "recommendation": "recommandation courte"
                }

                Règles de score :
                - FAIBLE si score < 0.4
                - MOYEN si score entre 0.4 et 0.7
                - ELEVE si score >= 0.7
                - needsHumanReview = true si risque MOYEN ou ELEVE
                - Si le risque est FAIBLE, needsHumanReview peut être false.
                """.formatted(
                claim.getId(),
                claim.getIncidentDate(),
                safe(claim.getDescription()),

                claim.getPolicy() != null ? claim.getPolicy().getPolicyNumber() : "N/A",
                claim.getPolicy() != null ? claim.getPolicy().getType() : "N/A",
                claim.getPolicy() != null ? claim.getPolicy().getStartDate() : "N/A",
                claim.getPolicy() != null ? claim.getPolicy().getEndDate() : "N/A",
                daysSincePolicyStart,

                claimsCount,
                recentClaimsCount,

                routeurResult,
                validateurResult,
                estimateurResult
        );
    }

    private FraudAnalysisResult parseFraudResponse(String rawResponse) {
        try {
            String jsonText = extractJson(rawResponse);
            JsonNode root = objectMapper.readTree(jsonText);

            List<String> reasons = new ArrayList<>();

            JsonNode reasonsNode = root.get("reasons");
            if (reasonsNode != null && reasonsNode.isArray()) {
                for (JsonNode reason : reasonsNode) {
                    reasons.add(reason.asText());
                }
            }

            String fraudRisk = root.path("fraudRisk").asText("MOYEN").toUpperCase(Locale.ROOT);
            double score = root.path("score").asDouble(0.5);
            boolean needsHumanReview = root.path("needsHumanReview").asBoolean(score >= 0.4);
            String recommendation = root.path("recommendation").asText("Vérification complémentaire recommandée.");

            if (!fraudRisk.equals("FAIBLE")
                    && !fraudRisk.equals("MOYEN")
                    && !fraudRisk.equals("ELEVE")) {
                fraudRisk = score >= 0.7 ? "ELEVE" : score >= 0.4 ? "MOYEN" : "FAIBLE";
            }

            score = safeScore(score);

            if (score >= 0.4) {
                needsHumanReview = true;
            }

            return FraudAnalysisResult.builder()
                    .fraudRisk(fraudRisk)
                    .score(score)
                    .reasons(reasons)
                    .needsHumanReview(needsHumanReview)
                    .recommendation(recommendation)
                    .build();

        } catch (Exception e) {
            log.warn("AgentFraude - réponse JSON invalide : {}", e.getMessage());

            return FraudAnalysisResult.builder()
                    .fraudRisk("MOYEN")
                    .score(0.5)
                    .reasons(List.of("La réponse IA n'a pas pu être analysée correctement."))
                    .needsHumanReview(true)
                    .recommendation("Vérification humaine recommandée.")
                    .build();
        }
    }

    private FraudAnalysisResult correctFalsePositiveRisk(
            FraudAnalysisResult result,
            Claim claim,
            List<AgentResult> previousAgentResults,
            List<Claim> clientClaims
    ) {
        if (result == null) {
            return FraudAnalysisResult.builder()
                    .fraudRisk("MOYEN")
                    .score(0.5)
                    .reasons(List.of("Analyse du risque incomplète."))
                    .needsHumanReview(true)
                    .recommendation("Vérification humaine recommandée.")
                    .build();
        }

        String risk = result.getFraudRisk() != null
                ? result.getFraudRisk().toUpperCase(Locale.ROOT)
                : "MOYEN";

        double score = safeScore(result.getScore());

        boolean validationCovered = isValidationCovered(previousAgentResults);
        boolean noRecentClaims = countRecentClaims(clientClaims, claim) == 0;

        long daysSincePolicyStart = calculateDaysSincePolicyStart(claim);
        boolean policyNotVeryRecent = daysSincePolicyStart < 0 || daysSincePolicyStart > 30;

        boolean hasStrongSignals = hasStrongFraudSignals(
                result,
                claim,
                clientClaims,
                daysSincePolicyStart
        );

        boolean hasWeakOnlySignals = hasOnlyWeakSignals(result);

        /*
         * Cas fréquent :
         * - dossier couvert
         * - pas d'historique suspect
         * - police pas très récente
         * - risque élevé basé surtout sur image/documents/validation humaine
         *
         * Alors ELEVE est trop agressif.
         */
        if ("ELEVE".equals(risk)
                && validationCovered
                && noRecentClaims
                && policyNotVeryRecent
                && !hasStrongSignals) {

            result.setFraudRisk("MOYEN");
            result.setScore(0.45);
            result.setNeedsHumanReview(true);
            result.setRecommendation(
                    "Vérification complémentaire recommandée pour confirmer les dommages, sans indication forte de fraude."
            );

            addReasonIfMissing(
                    result,
                    "Le dossier nécessite une vérification complémentaire, mais les éléments disponibles ne suffisent pas à qualifier un risque élevé."
            );

            return result;
        }

        /*
         * Si le risque est ELEVE uniquement à cause de signaux faibles,
         * on le réduit à MOYEN.
         */
        if ("ELEVE".equals(risk) && hasWeakOnlySignals && !hasStrongSignals) {
            result.setFraudRisk("MOYEN");
            result.setScore(Math.min(score, 0.55));
            result.setNeedsHumanReview(true);
            result.setRecommendation(
                    "Vérification complémentaire recommandée avant décision finale."
            );

            return result;
        }

        /*
         * Si le risque est MOYEN mais que tout est cohérent,
         * on peut rester MOYEN si une expertise est utile,
         * ou baisser vers FAIBLE si aucun signal utile n'existe.
         */
        if ("MOYEN".equals(risk)
                && validationCovered
                && noRecentClaims
                && policyNotVeryRecent
                && !hasStrongSignals
                && !hasWeakOnlySignals) {

            result.setFraudRisk("FAIBLE");
            result.setScore(0.30);
            result.setNeedsHumanReview(false);
            result.setRecommendation(
                    "Aucune indication significative de risque. Traitement normal possible."
            );

            return result;
        }

        result.setScore(score);

        if (score >= 0.7) {
            result.setFraudRisk("ELEVE");
            result.setNeedsHumanReview(true);
        } else if (score >= 0.4) {
            result.setFraudRisk("MOYEN");
            result.setNeedsHumanReview(true);
        } else {
            result.setFraudRisk("FAIBLE");
        }

        return result;
    }

    /*
     * Conclusion courte pour éviter l'erreur VARCHAR(255).
     * Ne mets pas toutes les raisons ici.
     */
    private String buildConclusion(FraudAnalysisResult result) {
        if (result == null) {
            return "Risque vérification : MOYEN | score : 0.5";
        }

        String risk = result.getFraudRisk() != null
                ? result.getFraudRisk()
                : "MOYEN";

        Double score = result.getScore() != null
                ? safeScore(result.getScore())
                : 0.5;

        return "Risque vérification : " + risk
                + " | score : " + score
                + " | revue humaine : " + (result.isNeedsHumanReview() ? "oui" : "non");
    }

    /*
     * Raw court et lisible.
     * Si ta colonne raw_llm_response est encore VARCHAR(255), ça passe.
     */
    private String buildCompactRawResponse(FraudAnalysisResult result) {
        if (result == null) {
            return "{\"fraudRisk\":\"MOYEN\",\"score\":0.5,\"needsHumanReview\":true}";
        }

        String risk = escapeJson(result.getFraudRisk() != null ? result.getFraudRisk() : "MOYEN");
        double score = safeScore(result.getScore());
        boolean human = result.isNeedsHumanReview();
        String recommendation = escapeJson(
                result.getRecommendation() != null
                        ? result.getRecommendation()
                        : "Vérification complémentaire recommandée."
        );

        return """
                {"fraudRisk":"%s","score":%.2f,"needsHumanReview":%s,"recommendation":"%s"}
                """.formatted(
                risk,
                score,
                human,
                recommendation
        ).trim();
    }

    private boolean isValidationCovered(List<AgentResult> previousAgentResults) {
        String validation = findAgentRaw(previousAgentResults, "validateur");
        String normalized = normalize(validation);

        return normalized.contains("couvert")
                && !normalized.contains("exclu")
                && !normalized.contains("non couvert");
    }

    private boolean hasStrongFraudSignals(
            FraudAnalysisResult result,
            Claim claim,
            List<Claim> clientClaims,
            long daysSincePolicyStart
    ) {
        int recentClaims = countRecentClaims(clientClaims, claim);

        if (recentClaims >= 2) {
            return true;
        }

        if (daysSincePolicyStart >= 0 && daysSincePolicyStart <= 7) {
            return true;
        }

        String reasonsText = normalize(String.join(" ", safeReasons(result)));

        return reasonsText.contains("plusieurs sinistres")
                || reasonsText.contains("documents contradictoires")
                || reasonsText.contains("contradiction majeure")
                || reasonsText.contains("montant clairement anormal")
                || reasonsText.contains("date de souscription tres proche")
                || reasonsText.contains("historique suspect");
    }

    private boolean hasOnlyWeakSignals(FraudAnalysisResult result) {
        List<String> reasons = safeReasons(result);

        if (reasons.isEmpty()) {
            return false;
        }

        String reasonsText = normalize(String.join(" ", reasons));

        boolean hasWeak =
                reasonsText.contains("validation humaine")
                        || reasonsText.contains("verification")
                        || reasonsText.contains("devis")
                        || reasonsText.contains("expertise")
                        || reasonsText.contains("photo")
                        || reasonsText.contains("image")
                        || reasonsText.contains("document manquant")
                        || reasonsText.contains("documents manquants")
                        || reasonsText.contains("photo insuffisante");

        boolean hasStrong =
                reasonsText.contains("plusieurs sinistres")
                        || reasonsText.contains("contradiction majeure")
                        || reasonsText.contains("documents contradictoires")
                        || reasonsText.contains("montant clairement anormal")
                        || reasonsText.contains("historique suspect")
                        || reasonsText.contains("souscription tres recente");

        return hasWeak && !hasStrong;
    }

    private void addReasonIfMissing(FraudAnalysisResult result, String reason) {
        if (result.getReasons() == null) {
            result.setReasons(new ArrayList<>());
        }

        boolean exists = result.getReasons().stream()
                .anyMatch(r -> normalize(r).equals(normalize(reason)));

        if (!exists) {
            result.getReasons().add(reason);
        }
    }

    private List<String> safeReasons(FraudAnalysisResult result) {
        if (result == null || result.getReasons() == null) {
            return List.of();
        }

        return result.getReasons();
    }

    private String findAgentRaw(List<AgentResult> results, String keyword) {
        if (results == null || keyword == null) {
            return "Non disponible";
        }

        return results.stream()
                .filter(result -> result.getAgentName() != null)
                .filter(result -> result.getAgentName().toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))
                .findFirst()
                .map(result -> {
                    if (result.getRawLlmResponse() != null && !result.getRawLlmResponse().isBlank()) {
                        return limitPromptContext(result.getRawLlmResponse());
                    }
                    return limitPromptContext(result.getConclusion());
                })
                .orElse("Non disponible");
    }

    private long calculateDaysSincePolicyStart(Claim claim) {
        try {
            if (claim.getPolicy() == null
                    || claim.getPolicy().getStartDate() == null
                    || claim.getIncidentDate() == null) {
                return -1;
            }

            return ChronoUnit.DAYS.between(
                    claim.getPolicy().getStartDate(),
                    claim.getIncidentDate()
            );

        } catch (Exception e) {
            return -1;
        }
    }

    private int countRecentClaims(List<Claim> claims, Claim currentClaim) {
        if (claims == null || currentClaim == null || currentClaim.getIncidentDate() == null) {
            return 0;
        }

        LocalDate currentIncidentDate = currentClaim.getIncidentDate();

        return (int) claims.stream()
                .filter(claim -> claim.getIncidentDate() != null)
                .filter(claim -> claim.getId() == null || !claim.getId().equals(currentClaim.getId()))
                .filter(claim -> {
                    long days = Math.abs(ChronoUnit.DAYS.between(
                            claim.getIncidentDate(),
                            currentIncidentDate
                    ));
                    return days <= 90;
                })
                .count();
    }

    private String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return "{}";
        }

        String clean = text
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        int start = clean.indexOf("{");
        int end = clean.lastIndexOf("}");

        if (start >= 0 && end > start) {
            return clean.substring(start, end + 1);
        }

        return clean;
    }

    private double safeScore(Double score) {
        if (score == null) {
            return 0.5;
        }

        if (score < 0.0) {
            return 0.0;
        }

        if (score > 1.0) {
            return 1.0;
        }

        return score;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String limitForDb(String value) {
        if (value == null) {
            return "";
        }

        String clean = value.trim();

        if (clean.length() <= DB_SAFE_TEXT_LENGTH) {
            return clean;
        }

        return clean.substring(0, DB_SAFE_TEXT_LENGTH) + "...";
    }

    private String limitPromptContext(String value) {
        if (value == null || value.isBlank()) {
            return "Non disponible";
        }

        String clean = value.trim();

        if (clean.length() <= 1200) {
            return clean;
        }

        return clean.substring(0, 1200) + "...";
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return normalized
                .toLowerCase(Locale.ROOT)
                .replace("’", "'")
                .replace("é", "e")
                .replace("è", "e")
                .replace("ê", "e")
                .replace("à", "a")
                .replace("ç", "c")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ")
                .trim();
    }
}