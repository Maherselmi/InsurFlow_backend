package tn.esprit.insureflow_back.infrastructure.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.insureflow_back.domain.enums.ClaimStatus;
import tn.esprit.insureflow_back.domain.model.Claim;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long> {

    @Query("SELECT DISTINCT c FROM Claim c " +
            "LEFT JOIN FETCH c.policy p " +
            "LEFT JOIN FETCH p.client " +       // ✅ charge le client via policy
            "LEFT JOIN FETCH c.client " +        // ✅ charge le client direct
            "LEFT JOIN FETCH c.documents")
    List<Claim> findAllWithClient();

    // 🆕 Charger le claim avec ses documents en même temps
    @Query("SELECT c FROM Claim c LEFT JOIN FETCH c.documents WHERE c.id = :id")
    Optional<Claim> findByIdWithDocuments(@Param("id") Long id);

    List<Claim> findByClientIdOrderByCreatedAtDesc(Long clientId);
    List<Claim> findByStatus(ClaimStatus status);
    List<Claim> findClaimsByClientId(@Param("clientId") Long clientId);
    List<Claim> findByPolicy_Client_IdOrderByCreatedAtDesc(Long clientId);
}