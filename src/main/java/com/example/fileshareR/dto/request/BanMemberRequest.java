package com.example.fileshareR.dto.request;

import com.example.fileshareR.enums.BanType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BanMemberRequest {

    @NotNull(message = "userId không được để trống")
    private Long userId;

    @NotNull(message = "Loại ban không được để trống")
    private BanType banType;

    private String reason;
}
