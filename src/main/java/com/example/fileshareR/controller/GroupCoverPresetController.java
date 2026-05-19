package com.example.fileshareR.controller;

import com.example.fileshareR.dto.response.GroupCoverPresetResponse;
import com.example.fileshareR.service.GroupCoverPresetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public endpoint for users to pick from active cover presets when creating/editing a group.
 */
@RestController
@RequestMapping("/api/group-cover-presets")
@RequiredArgsConstructor
public class GroupCoverPresetController {

    private final GroupCoverPresetService service;

    @GetMapping
    public List<GroupCoverPresetResponse> listActive() {
        return service.listActive();
    }
}
