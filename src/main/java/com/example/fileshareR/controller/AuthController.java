package com.example.fileshareR.controller;

import com.example.fileshareR.dto.request.ChangePasswordRequest;
import com.example.fileshareR.dto.request.LoginRequest;
import com.example.fileshareR.dto.request.RegisterRequest;
import com.example.fileshareR.dto.request.UpdateProfileRequest;
import com.example.fileshareR.dto.response.AuthResponse;
import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.service.AuthService;
import com.example.fileshareR.service.AvatarService;
import com.example.fileshareR.service.UserService;
import com.example.fileshareR.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AvatarService avatarService;
    private final UserService userService;

    @Value("${filesharer.jwt.access-token-validity-in-seconds}")
    private long accessTokenExpiration;

    @Value("${filesharer.jwt.refresh-token-validity-in-seconds}")
    private long refreshTokenExpiration;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        String response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/firebase-login")
    public ResponseEntity<AuthResponse> firebaseLogin(@RequestBody java.util.Map<String, String> body) {
        String idToken = body.get("idToken");
        AuthResponse response = authService.firebaseLogin(idToken);
        response.setExpiresIn(accessTokenExpiration);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        response.setExpiresIn(accessTokenExpiration);

        // Set refresh token as HTTP-only cookie
        ResponseCookie refreshTokenCookie = ResponseCookie
                .from("refresh_token", response.getRefreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(refreshTokenExpiration)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(response);
    }

    @GetMapping("/account")
    public ResponseEntity<AuthResponse.UserInfo> getAccount() {
        AuthResponse.UserInfo userInfo = authService.getAccount();
        return ResponseEntity.ok(userInfo);
    }

    @GetMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @CookieValue(name = "refresh_token", required = false) String cookieRefreshToken,
            @RequestHeader(name = "X-Refresh-Token", required = false) String headerRefreshToken) {

        // Ưu tiên lấy từ header (cho mobile), fallback về cookie (cho web)
        String refresh_token = headerRefreshToken != null ? headerRefreshToken : cookieRefreshToken;

        AuthResponse response = authService.refreshToken(refresh_token);
        response.setExpiresIn(accessTokenExpiration);

        // Set refresh token cookie
        ResponseCookie refreshTokenCookie = ResponseCookie
                .from("refresh_token", response.getRefreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(refreshTokenExpiration)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        authService.logout(request);

        // Xóa refresh token cookie
        ResponseCookie deleteCookie = ResponseCookie
                .from("refresh_token", null)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body("Đăng xuất thành công");
    }

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok("Đổi mật khẩu thành công");
    }

    @PutMapping("/profile")
    public ResponseEntity<AuthResponse.UserInfo> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        AuthResponse.UserInfo userInfo = authService.updateProfile(request);
        return ResponseEntity.ok(userInfo);
    }

    @PostMapping("/avatar")
    public ResponseEntity<java.util.Map<String, String>> uploadAvatar(
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ACCESS_TOKEN));
        User user = userService.getUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String ext = file.getOriginalFilename() != null
                ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.') + 1)
                : "jpg";
        String path = "avatars/users/" + user.getId() + "." + ext;
        String url = avatarService.uploadAvatar(file, path);

        user.setAvatarUrl(url);
        userService.updateUser(user);

        return ResponseEntity.ok(java.util.Map.of("avatarUrl", url));
    }
}
