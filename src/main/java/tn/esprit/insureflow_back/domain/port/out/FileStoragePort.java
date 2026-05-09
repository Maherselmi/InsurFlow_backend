package tn.esprit.insureflow_back.domain.port.out;

public interface FileStoragePort {

    String saveFile(
            String originalFileName,
            String contentType,
            byte[] content
    );

    byte[] readFile(String filePath);

    void deleteFile(String filePath);
}