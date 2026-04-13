package com.example.fileshareR.service;

import com.example.fileshareR.dto.request.ChangePasswordRequest;
import com.example.fileshareR.dto.request.LoginRequest;
import com.example.fileshareR.dto.request.RegisterRequest;
import com.example.fileshareR.dto.request.UpdateProfileRequest;
import com.example.fileshareR.dto.response.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {
    String register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse.UserInfo getAccount();

    AuthResponse refreshToken(String refreshToken);

    void logout(HttpServletRequest request);

    void changePassword(ChangePasswordRequest request);

    AuthResponse.UserInfo updateProfile(UpdateProfileRequest request);

    /** Đăng nhập bằng Firebase ID token (Google/Facebook) */
    AuthResponse firebaseLogin(String idToken);
}
