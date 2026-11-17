package com.green.keyboard_project.service;

import com.green.keyboard_project.exceptions.InvalidProductImageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProductImageStorageService {
    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private final Path uploadDirectory;
    private final String publicBaseUrl;

    public ProductImageStorageService(
            @Value("${app.upload.directory:./uploads/products}") String directory,
            @Value("${app.upload.public-base-url:http://localhost:8080}") String publicBaseUrl
    ) {
        this.uploadDirectory = Path.of(directory).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
    }

    public String store(MultipartFile file) {
        if (file.getSize() > MAX_SIZE) throw new InvalidProductImageException("이미지 용량은 5MB 이하만 업로드할 수 있습니다.");
        String extension = extension(file.getOriginalFilename());
        String contentType = file.getContentType();
        if (extension == null || contentType == null || !contentType.equalsIgnoreCase(mimeFor(extension))) {
            throw new InvalidProductImageException("JPG, JPEG, PNG, WEBP 이미지 파일만 업로드할 수 있습니다.");
        }
        try {
            byte[] bytes = file.getBytes();
            if (!hasValidSignature(bytes, extension)) {
                throw new InvalidProductImageException("이미지 파일 형식이 올바르지 않습니다.");
            }
            Files.createDirectories(uploadDirectory);
            String filename = UUID.randomUUID() + "." + extension;
            Files.write(uploadDirectory.resolve(filename), bytes);
            return publicBaseUrl + "/uploads/products/" + filename;
        } catch (IOException e) {
            throw new IllegalStateException("이미지를 저장할 수 없습니다.", e);
        }
    }

    public void deleteByUrl(String url) {
        if (url == null || !url.contains("/uploads/products/")) return;
        String filename = url.substring(url.lastIndexOf('/') + 1);
        if (!filename.matches("[0-9a-fA-F-]{36}\\.(jpg|png|webp)")) return;
        try {
            Files.deleteIfExists(uploadDirectory.resolve(filename).normalize());
        } catch (IOException e) {
            throw new IllegalStateException("기존 이미지를 삭제할 수 없습니다.", e);
        }
    }

    public Path getUploadDirectory() { return uploadDirectory; }

    private String extension(String originalFilename) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (extension == null) return null;
        extension = extension.toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "jpeg" -> "jpg";
            case "jpg", "png", "webp" -> extension;
            default -> null;
        };
    }

    private String mimeFor(String extension) {
        return switch (extension) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> "image/webp";
        };
    }

    private boolean hasValidSignature(byte[] bytes, String extension) {
        if (extension.equals("jpg")) return bytes.length >= 3 && (bytes[0] & 255) == 0xff && (bytes[1] & 255) == 0xd8 && (bytes[2] & 255) == 0xff;
        if (extension.equals("png")) {
            byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            if (bytes.length < signature.length) return false;
            for (int i = 0; i < signature.length; i++) if (bytes[i] != signature[i]) return false;
            return true;
        }
        return bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }
}
