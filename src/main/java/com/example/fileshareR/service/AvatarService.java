package com.example.fileshareR.service;

import org.springframework.web.multipart.MultipartFile;

public interface AvatarService {
    /** Upload avatar → Firebase Storage, trả về public URL */
    String uploadAvatar(MultipartFile file, String path);
}
