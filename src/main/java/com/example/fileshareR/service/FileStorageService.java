package com.example.fileshareR.service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorageService {

    /**
     * Lưu file vào hệ thống
     * @param file MultipartFile
     * @param userId ID của user upload
     * @return Đường dẫn file đã lưu
     */
    String storeFile(MultipartFile file, Long userId);

    /**
     * Xóa file khỏi hệ thống
     * @param fileUrl Đường dẫn file cần xóa
     */
    void deleteFile(String fileUrl);

    /**
     * Lấy đường dẫn đầy đủ của file
     * @param fileUrl Đường dẫn tương đối
     * @return Path đầy đủ
     */
    Path getFilePath(String fileUrl);

    /**
     * Kiểm tra file có tồn tại không
     * @param fileUrl Đường dẫn file
     * @return true nếu file tồn tại
     */
    boolean fileExists(String fileUrl);
}
