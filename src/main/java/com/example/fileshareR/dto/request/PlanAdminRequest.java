package com.example.fileshareR.dto.request;

import lombok.Data;

/**
 * Admin CRUD payload for Plan. All fields optional on PATCH — null values
 * skip the corresponding setter. POST requires code + name + quotaBytes +
 * priceVnd (description optional).
 */
@Data
public class PlanAdminRequest {
    private String code;        // required on create; immutable after
    private String name;
    private Long quotaBytes;
    private Long priceVnd;
    private String description;
}
