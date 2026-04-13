package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.service.FileStorageService;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.StorageClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Primary
@Slf4j
public class FirebaseStorageServiceImpl implements FileStorageService {

    @Value("${firebase.storage.bucket:filesharer-b8ff3}")
    private String bucketName;

    @Value("${filesharer.file-storage.allowed-extensions:pdf,docx,doc,txt}")
    private String allowedExtensionsStr;

    private Storage getStorage() {
        return StorageClient.getInstance(FirebaseApp.getInstance()).bucket(bucketName).getStorage();
    }

    @Override
    public String storeFile(MultipartFile file, Long userId) {
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        log.info("Storing file '{}' for user {} to Firebase Storage", originalFileName, userId);

        // Validate extension
        String extension = getExtension(originalFileName);
        List<String> allowed = Arrays.asList(allowedExtensionsStr.split(","));
        if (!allowed.contains(extension.toLowerCase())) {
            throw new CustomException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        // Generate path: YYYY/MM/DD/userId_uuid_filename.ext
        LocalDate now = LocalDate.now();
        String datePath = String.format("%d/%02d/%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        String safeFileName = userId + "_" + UUID.randomUUID().toString().substring(0, 8)
                + "_" + sanitizeFileName(originalFileName);
        String objectPath = datePath + "/" + safeFileName;

        try {
            BlobId blobId = BlobId.of(bucketName, objectPath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(file.getContentType())
                    .build();
            getStorage().create(blobInfo, file.getBytes());
            log.info("File stored in Firebase Storage: {}", objectPath);
            return objectPath;
        } catch (IOException e) {
            log.error("Failed to store file in Firebase Storage: {}", e.getMessage());
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;
        log.info("Deleting file from Firebase Storage: {}", fileUrl);
        try {
            BlobId blobId = BlobId.of(bucketName, fileUrl);
            getStorage().delete(blobId);
        } catch (Exception e) {
            log.warn("Failed to delete file from Firebase Storage: {} - {}", fileUrl, e.getMessage());
        }
    }

    @Override
    public Path getFilePath(String fileUrl) {
        // Download từ Firebase Storage → temp file (để DocumentServiceImpl đọc cho NLP + download)
        log.debug("Downloading file from Firebase Storage to temp: {}", fileUrl);
        try {
            BlobId blobId = BlobId.of(bucketName, fileUrl);
            Blob blob = getStorage().get(blobId);
            if (blob == null || !blob.exists()) {
                throw new CustomException(ErrorCode.DOCUMENT_NOT_FOUND);
            }

            String extension = getExtension(fileUrl);
            Path tempFile = Files.createTempFile("filesharer_", "." + extension);
            blob.downloadTo(tempFile);
            tempFile.toFile().deleteOnExit(); // Tự xóa khi JVM shutdown
            return tempFile;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to download file from Firebase Storage: {}", e.getMessage());
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public boolean fileExists(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return false;
        try {
            BlobId blobId = BlobId.of(bucketName, fileUrl);
            Blob blob = getStorage().get(blobId);
            return blob != null && blob.exists();
        } catch (Exception e) {
            return false;
        }
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(dotIndex + 1) : "";
    }

    private String sanitizeFileName(String fileName) {
        // Bỏ ký tự đặc biệt, giữ chữ + số + dấu chấm + gạch dưới + gạch ngang
        return fileName.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
