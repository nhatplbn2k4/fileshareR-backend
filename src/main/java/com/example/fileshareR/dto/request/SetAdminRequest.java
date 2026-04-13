package com.example.fileshareR.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetAdminRequest {

    @NotNull(message = "userId không được để trống")
    private Long userId;

    @NotNull(message = "isAdmin không được để trống")
    private Boolean isAdmin;
}
