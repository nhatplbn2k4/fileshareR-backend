package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.service.AvatarService;
import com.google.cloud.storage.Acl;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.StorageClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class AvatarServiceImpl implements AvatarService {

    @Value("${firebase.storage.bucket:filesharer-b8ff3}")
    private String bucketName;

    private static final List<String> ALLOWED_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_SIZE = 10 * 1024 * 1024; // 10MB

    private Storage getStorage() {
        return StorageClient.getInstance(FirebaseApp.getInstance()).bucket(bucketName).getStorage();
    }

    @Override
    public String uploadAvatar(MultipartFile file, String path) {
        // Validate
        if (file.isEmpty())
            throw new CustomException(ErrorCode.BAD_REQUEST, "File trống");
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "Chỉ chấp nhận ảnh JPG, PNG, GIF, WebP");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "Ảnh tối đa 10MB");
        }

        try {
            BlobId blobId = BlobId.of(bucketName, path);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(file.getContentType())
                    .build();
            var blob = getStorage().create(blobInfo, file.getBytes());

            // Thử set public ACL. Nếu bucket dùng Uniform access → bỏ qua, dùng signed URL
            // hoặc media link
            try {
                blob.createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER));
            } catch (Exception e) {
                log.warn("Cannot set public ACL (bucket may use uniform access): {}", e.getMessage());
            }

            // Dùng media link (luôn hoạt động cho Firebase Storage)
            String publicUrl = blob.getMediaLink();
            if (publicUrl == null) {
                publicUrl = String.format("https://storage.googleapis.com/%s/%s", bucketName, path);
            }
            log.info("Avatar uploaded: {}", publicUrl);
            return publicUrl;
        } catch (IOException e) {
            log.error("Avatar upload failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
