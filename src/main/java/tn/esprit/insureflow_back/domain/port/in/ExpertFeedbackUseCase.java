package tn.esprit.insureflow_back.domain.port.in;

import tn.esprit.insureflow_back.domain.model.ExpertFeedback;

import java.util.List;

public interface ExpertFeedbackUseCase {

    ExpertFeedback saveExpertFeedback(ExpertFeedback feedback);

    ExpertFeedback getExpertFeedbackById(Long id);

    ExpertFeedback getExpertFeedbackByClaimId(Long claimId);

    List<ExpertFeedback> getAllExpertFeedbacks();

    void deleteExpertFeedback(Long id);
}