package tn.esprit.insureflow_back.domain.enums;

public enum ClaimStatus {
    SUBMITTED,
    IN_ANALYSIS,
    PENDING_VALIDATION,
    APPROVED,    // 🆕 ajouter si manquant
    REJECTED,    // 🆕 ajouter si manquant
    CLOSED
}