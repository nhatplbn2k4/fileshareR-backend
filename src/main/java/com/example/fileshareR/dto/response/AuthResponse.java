package com.example.fileshareR.dto.response;

import com.example.fileshareR.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private Long expiresIn;
    private UserInfo user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long userId;
        private String email;
        private String fullName;
        private String avatarUrl;
        private UserRole role;
        private Boolean isActive;
        private Boolean emailVerified;
        private String authProvider; // LOCAL, GOOGLE, FACEBOOK
    }
}
