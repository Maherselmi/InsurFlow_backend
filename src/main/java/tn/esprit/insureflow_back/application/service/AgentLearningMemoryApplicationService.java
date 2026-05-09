package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.domain.enums.AgentName;
import tn.esprit.insureflow_back.domain.model.AgentLearningFeedback;
import tn.esprit.insureflow_back.domain.port.out.AgentLearningFeedbackRepositoryPort;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentLearningMemoryApplicationService {

    private static final int MAX_FIELD_CHARS = 900;
    private static final int MAX_CORRECTIONS = 3;
    private static final int MAX_VALIDATED = 2;

    private final AgentLearningFeedbackRepositoryPort feedbackRepositoryPort;

    public String buildMemoryBlock(AgentName agentName, Long currentClaimId) {

        List<AgentLearningFeedback> examples =
                feedbackRepositoryPort.findLearningExamples(
                        agentName,
                        currentClaimId,
                        PageRequest.of(0, 20)
                );

        List<AgentLearningFeedback> corrections = examples.stream()
                .filter(this::isUsable)
                .filter(f -> Boolean.FALSE.equals(f.getWasCorrect()))
                .sorted(Comparator.comparing(
                        AgentLearningFeedback::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(MAX_CORRECTIONS)
                .collect(Collectors.toList());

        List<AgentLearningFeedback> validated = examples.stream()
                .filter(this::isUsable)
                .filter(f -> Boolean.TRUE.equals(f.getWasCorrect()))
                .sorted(Comparator.comparing(
                        AgentLearningFeedback::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(MAX_VALIDATED)
                .collect(Collectors.toList());

        StringBuilder block = new StringBuilder();

        if (!corrections.isEmpty()) {
            block.append("=== CORRECTIONS EXPERTES A APPLIQUER ===\n");
            block.append("IMPORTANT : Ces cas ont été corrigés par l'expert. ")
                    .append("Adapte ton analyse en conséquence.\n\n");

            corrections.stream()
                    .map(this::formatExample)
                    .forEach(s -> block.append(s).append("\n\n"));
        }

        if (!validated.isEmpty()) {
            block.append("=== EXEMPLES VALIDES PAR EXPERT ===\n\n");

            validated.stream()
                    .map(this::formatExample)
                    .forEach(s -> block.append(s).append("\n\n"));
        }

        return block.toString().trim();
    }

    private boolean isUsable(AgentLearningFeedback feedback) {
        return feedback != null
                && Boolean.TRUE.equals(feedback.getUseForLearning())
                && hasText(feedback.getFinalValidatedOutput());
    }

    private String formatExample(AgentLearningFeedback feedback) {
        boolean correct = Boolean.TRUE.equals(feedback.getWasCorrect());
        String label = correct
                ? "EXEMPLE VALIDE PAR EXPERT"
                : "CORRECTION EXPERT A APPRENDRE";

        return """
                %s
                Resultat agent correct : %s
                Satisfaction expert : %s/5
                Commentaire expert : %s

                Entree dossier :
                %s

                Sortie agent initiale :
                %s

                Sortie finale validee par expert :
                %s
                """.formatted(
                label,
                correct ? "OUI" : "NON",
                feedback.getSatisfactionScore() == null
                        ? "N/A"
                        : feedback.getSatisfactionScore().toString(),
                truncate(feedback.getExpertComment(), 250),
                truncate(feedback.getInputData(), MAX_FIELD_CHARS),
                truncate(feedback.getAgentOutput(), MAX_FIELD_CHARS),
                truncate(feedback.getFinalValidatedOutput(), MAX_FIELD_CHARS)
        ).trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String truncate(String value, int max) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() <= max) {
            return safe;
        }
        return safe.substring(0, max) + "...";
    }
}