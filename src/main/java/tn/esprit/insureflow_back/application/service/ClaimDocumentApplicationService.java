package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.insureflow_back.domain.model.Claim;
import tn.esprit.insureflow_back.domain.model.ClaimDocument;
import tn.esprit.insureflow_back.domain.port.out.ClaimDocumentRepositoryPort;
import tn.esprit.insureflow_back.domain.port.out.ClaimRepositoryPort;
import tn.esprit.insureflow_back.domain.port.out.FileStoragePort;

/**
 * Service applicatif responsable de la gestion des documents de sinistre.
 * Il permet d’uploader un fichier et de l’associer à un sinistre existant.
 */
@Service
@RequiredArgsConstructor
public class ClaimDocumentApplicationService {

    /*
     * Ports utilisés pour accéder aux documents, aux sinistres
     * et au système de stockage des fichiers.
     */
    private final ClaimDocumentRepositoryPort documentRepositoryPort;
    private final ClaimRepositoryPort claimRepositoryPort;
    private final FileStoragePort fileStoragePort;

    /*
     * Upload un fichier et l’associe au sinistre correspondant.
     */
    public ClaimDocument uploadFile(
            Long claimId,
            MultipartFile file
    ) throws Exception {

        /*
         * Recherche du sinistre par son identifiant.
         * Une exception est levée si le sinistre n’existe pas.
         */
        Claim claim = claimRepositoryPort.findById(claimId)
                .orElseThrow(() ->
                        new RuntimeException("Claim not found"));

        /*
         * Sauvegarde physique du fichier via le port de stockage.
         * Le chemin retourné sera enregistré dans la base de données.
         */
        String path = fileStoragePort.saveFile(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        );

        /*
         * Création de l’objet document lié au sinistre.
         */
        ClaimDocument doc = new ClaimDocument();

        doc.setClaim(claim);
        doc.setFileName(file.getOriginalFilename());
        doc.setFileType(file.getContentType());
        doc.setFilePath(path);

        /*
         * Sauvegarde des métadonnées du document en base.
         */
        return documentRepositoryPort.save(doc);
    }
}