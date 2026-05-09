package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.insureflow_back.domain.model.Claim;
import tn.esprit.insureflow_back.domain.model.ClaimDocument;
import tn.esprit.insureflow_back.domain.port.out.ClaimDocumentRepositoryPort;
import tn.esprit.insureflow_back.domain.port.out.ClaimRepositoryPort;
import tn.esprit.insureflow_back.domain.port.out.FileStoragePort;

@Service
@RequiredArgsConstructor
public class ClaimDocumentApplicationService {

    private final ClaimDocumentRepositoryPort documentRepositoryPort;
    private final ClaimRepositoryPort claimRepositoryPort;
    private final FileStoragePort fileStoragePort;

    public ClaimDocument uploadFile(
            Long claimId,
            MultipartFile file
    ) throws Exception {

        Claim claim = claimRepositoryPort.findById(claimId)
                .orElseThrow(() ->
                        new RuntimeException("Claim not found"));

        String path = fileStoragePort.saveFile(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        );

        ClaimDocument doc = new ClaimDocument();

        doc.setClaim(claim);
        doc.setFileName(file.getOriginalFilename());
        doc.setFileType(file.getContentType());
        doc.setFilePath(path);

        return documentRepositoryPort.save(doc);
    }
}