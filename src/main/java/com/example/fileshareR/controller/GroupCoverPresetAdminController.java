package com.example.fileshareR.controller;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.UpdateGroupCoverPresetRequest;
import com.example.fileshareR.dto.response.GroupCoverPresetResponse;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.service.GroupCoverPresetService;
import com.example.fileshareR.service.UserService;
import com.example.fileshareR.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Admin-only CRUD for group cover presets.
 * Security: under /api/admin/** — ADMIN role enforced at SecurityConfiguration + here.
 */
@RestController
@RequestMapping("/api/admin/group-cover-presets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class GroupCoverPresetAdminController {

    private final GroupCoverPresetService service;
    private final UserService userService;

    @GetMapping
    public List<GroupCoverPresetResponse> listAll() {
        return service.listAll();
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<GroupCoverPresetResponse> create(
            @RequestParam("name") String name,
            @RequestParam("file") MultipartFile file) {
        Long adminId = getCurrentUserId();
        return ResponseEntity.ok(service.create(name, file, adminId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<GroupCoverPresetResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateGroupCoverPresetRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private Long getCurrentUserId() {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ACCESS_TOKEN));
        User user = userService.getUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return user.getId();
    }
}
