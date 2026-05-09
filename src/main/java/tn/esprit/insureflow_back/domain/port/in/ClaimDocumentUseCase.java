package tn.esprit.insureflow_back.domain.port.in;

import tn.esprit.insureflow_back.domain.model.ClaimDocument;

import java.util.List;

public interface ClaimDocumentUseCase {

    ClaimDocument saveDocument(ClaimDocument document);

    ClaimDocument getDocumentById(Long id);

    List<ClaimDocument> getDocumentsByClaimId(Long claimId);

    void deleteDocument(Long id);
}