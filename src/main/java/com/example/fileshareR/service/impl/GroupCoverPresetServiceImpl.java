package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.UpdateGroupCoverPresetRequest;
import com.example.fileshareR.dto.response.GroupCoverPresetResponse;
import com.example.fileshareR.entity.GroupCoverPreset;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.repository.GroupCoverPresetRepository;
import com.example.fileshareR.repository.UserRepository;
import com.example.fileshareR.service.AvatarService;
import com.example.fileshareR.service.GroupCoverPresetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class GroupCoverPresetServiceImpl implements GroupCoverPresetService {

    private final GroupCoverPresetRepository repository;
    private final UserRepository userRepository;
    private final AvatarService avatarService;

    @Override
    @Transactional(readOnly = true)
    public List<GroupCoverPresetResponse> listActive() {
        return repository.findByIsActiveTrueOrderByDisplayOrderAscIdAsc()
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupCoverPresetResponse> listAll() {
        return repository.findAllByOrderByDisplayOrderAscIdAsc()
                .stream().map(this::toResponse).toList();
    }

    @Override
    public GroupCoverPresetResponse create(String name, MultipartFile file, Long adminId) {
        if (name == null || name.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "Tên preset không được trống");
        }
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "Vui lòng chọn ảnh");
        }

        String ext = "jpg";
        String original = file.getOriginalFilename();
        if (original != null && original.lastIndexOf('.') > 0) {
            ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase();
        }
        String path = "covers/presets/" + UUID.randomUUID() + "." + ext;
        String url = avatarService.uploadAvatar(file, path);

        User admin = userRepository.findById(adminId).orElse(null);
        Integer nextOrder = repository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .mapToInt(p -> p.getDisplayOrder() == null ? 0 : p.getDisplayOrder())
                .max()
                .orElse(-1) + 1;

        GroupCoverPreset preset = GroupCoverPreset.builder()
                .name(name.trim())
                .imageUrl(url)
                .isActive(true)
                .displayOrder(nextOrder)
                .createdBy(admin)
                .build();
        preset = repository.save(preset);
        log.info("Group cover preset {} created by admin {}", preset.getId(), adminId);
        return toResponse(preset);
    }

    @Override
    public GroupCoverPresetResponse update(Long presetId, UpdateGroupCoverPresetRequest request) {
        GroupCoverPreset preset = repository.findById(presetId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Preset không tồn tại"));
        if (request.getName() != null && !request.getName().isBlank()) {
            preset.setName(request.getName().trim());
        }
        if (request.getIsActive() != null) {
            preset.setIsActive(request.getIsActive());
        }
        if (request.getDisplayOrder() != null) {
            preset.setDisplayOrder(request.getDisplayOrder());
        }
        preset = repository.save(preset);
        return toResponse(preset);
    }

    @Override
    public void delete(Long presetId) {
        if (!repository.existsById(presetId)) {
            throw new CustomException(ErrorCode.NOT_FOUND, "Preset không tồn tại");
        }
        repository.deleteById(presetId);
        log.info("Group cover preset {} deleted", presetId);
    }

    private GroupCoverPresetResponse toResponse(GroupCoverPreset p) {
        return GroupCoverPresetResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .imageUrl(p.getImageUrl())
                .isActive(p.getIsActive())
                .displayOrder(p.getDisplayOrder())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
