package com.example.fileshareR.controller;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.response.DocumentResponse;
import com.example.fileshareR.dto.response.PublicProfileResponse;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.FolderVisibilityType;
import com.example.fileshareR.enums.VisibilityType;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.FolderRepository;
import com.example.fileshareR.dto.response.FolderResponse;
import com.example.fileshareR.service.DocumentService;
import com.example.fileshareR.service.FolderService;
import com.example.fileshareR.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final DocumentService documentService;
    private final FolderService folderService;

    /**
     * [PUBLIC] Hồ sơ công khai của user.
     * GET /api/users/{userId}/profile
     */
    @GetMapping("/{userId}/profile")
    public ResponseEntity<PublicProfileResponse> getPublicProfile(@PathVariable Long userId) {
        User user = userService.getUserById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        long publicDocCount = documentRepository.countByUserIdAndVisibility(userId, VisibilityType.PUBLIC);
        long publicFolderCount = folderRepository.countByUserIdAndVisibility(userId, FolderVisibilityType.PUBLIC);

        PublicProfileResponse response = PublicProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .publicDocumentCount(publicDocCount)
                .publicFolderCount(publicFolderCount)
                .createdAt(user.getCreatedAt())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * [PUBLIC] Tài liệu public của user.
     * GET /api/users/{userId}/documents
     */
    @GetMapping("/{userId}/documents")
    public ResponseEntity<List<DocumentResponse>> getUserPublicDocuments(@PathVariable Long userId) {
        userService.getUserById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return ResponseEntity.ok(documentService.getPublicDocumentsByUser(userId));
    }

    /**
     * [PUBLIC] Thư mục public của user.
     * GET /api/users/{userId}/folders
     */
    @GetMapping("/{userId}/folders")
    public ResponseEntity<List<FolderResponse>> getUserPublicFolders(@PathVariable Long userId) {
        userService.getUserById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return ResponseEntity.ok(folderService.getPublicFoldersByUser(userId));
    }
}
