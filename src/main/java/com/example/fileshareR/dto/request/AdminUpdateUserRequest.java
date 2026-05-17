package com.example.fileshareR.dto.request;

import lombok.Data;

/**
 * Partial update payload for admin user-management. Only non-null fields
 * are applied; nulls leave the existing value untouched.
 */
@Data
public class AdminUpdateUserRequest {
    private String planCode;     // FREE | PREMIUM | ...
    private Boolean isActive;    // suspend/activate
}
