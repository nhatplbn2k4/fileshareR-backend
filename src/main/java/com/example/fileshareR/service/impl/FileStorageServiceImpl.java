package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

    private final Path fileStorageLocation;
    private final List<String> allowedExtensions;

    public FileStorageServiceImpl(
            @Value("${filesharer.file-storage.upload-dir}") String uploadDir,
            @Value("${filesharer.file-storage.allowed-extensions}") String allowedExtensions) {

        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.allowedExtensions = Arrays.asList(allowedExtensions.split(","));

        try {
            Files.createDirectories(this.fileStorageLocation);
            log.info("File storage location created at: {}", this.fileStorageLocation);
        } catch (IOException ex) {
            log.error("Could not create upload directory", ex);
            throw new RuntimeException("Could not create upload directory", ex);
        }
    }

    @Override
    public String storeFile(MultipartFile file, Long userId) {
        // Validate file
        if (file.isEmpty()) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR);
        }

        // Get original filename
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());

        // Validate filename
        if (originalFilename.contains("..")) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR);
        }

        // Get file extension
        String fileExtension = getFileExtension(originalFilename);

        // Validate extension
        if (!allowedExtensions.contains(fileExtension.toLowerCase())) {
            throw new CustomException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        try {
            // Tạo tên file duy nhất: userId_date_uuid_originalname
            String uniqueFileName = generateUniqueFileName(originalFilename, userId);

            // Tạo thư mục theo ngày (uploads/2024/03/21/)
            LocalDate today = LocalDate.now();
            Path dailyDirectory = fileStorageLocation
                    .resolve(String.valueOf(today.getYear()))
                    .resolve(String.format("%02d", today.getMonthValue()))
                    .resolve(String.format("%02d", today.getDayOfMonth()));

            Files.createDirectories(dailyDirectory);

            // Đường dẫn file đầy đủ
            Path targetLocation = dailyDirectory.resolve(uniqueFileName);

            // Copy file
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            log.info("File stored successfully: {}", uniqueFileName);

            // Return relative path: 2024/03/21/filename.pdf
            return fileStorageLocation.relativize(targetLocation).toString().replace("\\", "/");

        } catch (IOException ex) {
            log.error("Could not store file {}", originalFilename, ex);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        try {
            Path filePath = fileStorageLocation.resolve(fileUrl).normalize();
            Files.deleteIfExists(filePath);
            log.info("File deleted successfully: {}", fileUrl);
        } catch (IOException ex) {
            log.error("Could not delete file {}", fileUrl, ex);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Path getFilePath(String fileUrl) {
        return fileStorageLocation.resolve(fileUrl).normalize();
    }

    @Override
    public boolean fileExists(String fileUrl) {
        Path filePath = getFilePath(fileUrl);
        return Files.exists(filePath);
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex + 1);
    }

    private String generateUniqueFileName(String originalFilename, Long userId) {
        String fileExtension = getFileExtension(originalFilename);
        String nameWithoutExtension = originalFilename.substring(0, originalFilename.lastIndexOf('.'));

        // Remove special characters from filename
        nameWithoutExtension = nameWithoutExtension.replaceAll("[^a-zA-Z0-9._-]", "_");

        // Generate: userId_uuid_name.ext
        return userId + "_" + UUID.randomUUID().toString() + "_" + nameWithoutExtension + "." + fileExtension;
    }
}
