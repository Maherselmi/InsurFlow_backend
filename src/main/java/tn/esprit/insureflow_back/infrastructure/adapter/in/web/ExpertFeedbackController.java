package tn.esprit.insureflow_back.infrastructure.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.insureflow_back.application.dto.ExpertFeedbackRequest;
import tn.esprit.insureflow_back.application.service.ExpertFeedbackLearningApplicationService;

import java.util.Map;

@RestController
@RequestMapping("/api/expert-feedback")
@RequiredArgsConstructor
public class ExpertFeedbackController {

    private final ExpertFeedbackLearningApplicationService expertFeedbackLearningService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> saveExpertFeedback(@RequestBody ExpertFeedbackRequest request) {
        int savedItems = expertFeedbackLearningService.saveExpertFeedback(request);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Expert feedback saved successfully",
                "learningItemsSaved", savedItems
        ));
    }
}