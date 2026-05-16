package com.example.genprofileimage.profile;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ProfilePhotoService {

    private final ComfyUiClient comfyUiClient;

    public ProfilePhotoService(ComfyUiClient comfyUiClient) {
        this.comfyUiClient = comfyUiClient;
    }

    public byte[] convertToSuitProfile(MultipartFile image) throws IOException {
        validateImage(image);

        Path tempFile = Files.createTempFile("profile-photo-upload-", getFileExtension(image.getOriginalFilename()));
        try {
            image.transferTo(tempFile);
            return comfyUiClient.generateSuitProfile(tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new InvalidImageUploadException("이미지 파일을 업로드해주세요.");
        }

        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidImageUploadException("이미지 파일만 업로드할 수 있습니다.");
        }
    }

    private String getFileExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return ".tmp";
        }

        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return ".tmp";
        }

        String extension = originalFilename.substring(dotIndex).toLowerCase();
        if (extension.length() > 12 || !extension.matches("\\.[a-z0-9]+")) {
            return ".tmp";
        }
        return extension;
    }
}
