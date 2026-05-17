package com.example.fileshareR.dto.request;

import lombok.Data;

/**
 * Admin CRUD payload for StorageAddon. All fields optional on PATCH.
 */
@Data
public class AddonAdminRequest {
    private String code;        // required on create; immutable after
    private String name;
    private Long extraBytes;
    private Long priceVnd;
    private String description;
}
