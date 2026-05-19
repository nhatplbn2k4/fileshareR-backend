package com.example.fileshareR.service;

import com.example.fileshareR.dto.request.UpdateGroupCoverPresetRequest;
import com.example.fileshareR.dto.response.GroupCoverPresetResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface GroupCoverPresetService {

    /** Public list for users (active only). */
    List<GroupCoverPresetResponse> listActive();

    /** Admin list — all rows (active + inactive). */
    List<GroupCoverPresetResponse> listAll();

    /** Admin create: upload image + name. */
    GroupCoverPresetResponse create(String name, MultipartFile file, Long adminId);

    /** Admin update: name, isActive, displayOrder. */
    GroupCoverPresetResponse update(Long presetId, UpdateGroupCoverPresetRequest request);

    /** Admin delete. */
    void delete(Long presetId);
}
