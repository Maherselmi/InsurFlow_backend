package tn.esprit.insureflow_back.infrastructure.adapter.out.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.domain.port.out.FileStoragePort;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
public class LocalFileStorageAdapter implements FileStoragePort {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public String saveFile(
            String originalFileName,
            String contentType,
            byte[] content
    ) {
        try {
            Path uploadPath = Paths.get(uploadDir)
                    .toAbsolutePath()
                    .normalize();

            Files.createDirectories(uploadPath);

            String safeFileName = sanitizeFileName(
                    originalFileName != null ? originalFileName : "document"
            );

            String storedFileName = System.currentTimeMillis()
                    + "_"
                    + UUID.randomUUID()
                    + "_"
                    + safeFileName;

            Path targetPath = uploadPath.resolve(storedFileName).normalize();

            if (!targetPath.startsWith(uploadPath)) {
                throw new RuntimeException("Nom de fichier invalide");
            }

            Files.write(targetPath, content);

            return targetPath.toString();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Erreur lors de la sauvegarde du fichier : " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public byte[] readFile(String filePath) {
        try {
            return Files.readAllBytes(Path.of(filePath));
        } catch (Exception e) {
            throw new RuntimeException(
                    "Erreur lors de la lecture du fichier : " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public void deleteFile(String filePath) {
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (Exception e) {
            throw new RuntimeException(
                    "Erreur lors de la suppression du fichier : " + e.getMessage(),
                    e
            );
        }
    }

    private String sanitizeFileName(String fileName) {
        return fileName
                .replace("\\", "_")
                .replace("/", "_")
                .replace("..", "_")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}