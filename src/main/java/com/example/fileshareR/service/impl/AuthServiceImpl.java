package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.ChangePasswordRequest;
import com.example.fileshareR.dto.request.LoginRequest;
import com.example.fileshareR.dto.request.RegisterRequest;
import com.example.fileshareR.dto.request.UpdateProfileRequest;
import com.example.fileshareR.dto.response.AuthResponse;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.AuthProvider;
import com.example.fileshareR.enums.UserRole;
import com.example.fileshareR.repository.PlanRepository;
import com.example.fileshareR.service.AuthService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.example.fileshareR.service.OtpService;
import com.example.fileshareR.service.TokenBlacklistService;
import com.example.fileshareR.service.UserService;
import com.example.fileshareR.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final SecurityUtil securityUtil;
    private final UserService userService;
    private final TokenBlacklistService tokenBlacklistService;
    private final OtpService otpService;
    private final PlanRepository planRepository;

    @Override
    public String register(RegisterRequest request) {
        log.info("Registering new user: {}", request.getEmail());
        String result = userService.createUser(request);

        // Gửi OTP xác thực email ngay sau khi đăng ký
        otpService.sendEmailVerificationOtp(request.getEmail());

        return result + ". Vui lòng kiểm tra email để xác thực tài khoản.";
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        log.info("User login attempt: {}", request.getEmail());

        // Xác thực người dùng
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                request.getEmail(), request.getPassword());

        Authentication authentication = authenticationManagerBuilder.getObject()
                .authenticate(authenticationToken);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Lấy thông tin user từ database
        User user = userService.getUserByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // Kiểm tra tài khoản có bị khóa không
        if (!user.getIsActive()) {
            log.warn("Login attempt for inactive user: {}", user.getEmail());
            throw new CustomException(ErrorCode.USER_NOT_ACTIVE);
        }

        // Kiểm tra email đã xác thực chưa
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            log.warn("Login attempt for unverified email: {}", user.getEmail());
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        // Tạo UserInfo
        AuthResponse.UserInfo userInfo = buildUserInfo(user);

        // Tạo tokens
        String accessToken = securityUtil.createAccessToken(authentication.getName(), userInfo);
        String refreshToken = securityUtil.createRefreshToken(request.getEmail(),
                AuthResponse.builder().user(userInfo).build());

        // Cập nhật refresh token trong database
        userService.updateUserToken(refreshToken, request.getEmail());

        log.info("User logged in successfully: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(userInfo)
                .build();
    }

    @Override
    public AuthResponse.UserInfo getAccount() {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ACCESS_TOKEN));

        User user = userService.getUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return buildUserInfo(user);
    }

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        if (refreshToken == null) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        log.info("Refreshing token");

        // Validate refresh token
        Jwt decodedToken = securityUtil.checkValidRefreshToken(refreshToken);
        String email = decodedToken.getSubject();

        // Kiểm tra user và token
        User user = userService.getUserByRefreshTokenAndEmail(refreshToken, email);
        if (user == null) {
            log.warn("Invalid refresh token for user: {}", email);
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // Tạo UserInfo
        AuthResponse.UserInfo userInfo = buildUserInfo(user);

        // Tạo tokens mới
        String newAccessToken = securityUtil.createAccessToken(email, userInfo);
        String newRefreshToken = securityUtil.createRefreshToken(email,
                AuthResponse.builder().user(userInfo).build());

        // Cập nhật refresh token trong database
        userService.updateUserToken(newRefreshToken, email);

        log.info("Token refreshed successfully for user: {}", email);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .user(userInfo)
                .build();
    }

    @Override
    public void logout(HttpServletRequest request) {
        log.info("User logout attempt");

        // Lấy access token từ header
        String accessToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (accessToken == null || !accessToken.startsWith("Bearer ")) {
            throw new CustomException(ErrorCode.INVALID_ACCESS_TOKEN);
        }

        accessToken = accessToken.substring(7);

        // Validate access token
        Jwt decodedToken = securityUtil.checkValidAccessToken(accessToken);
        String email = decodedToken.getSubject();

        if (email == null || email.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_ACCESS_TOKEN);
        }

        // Kiểm tra user có tồn tại không
        userService.getUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // Đưa access token vào blacklist
        tokenBlacklistService.blacklistToken(accessToken);

        // Xoá refresh token trong database
        userService.updateUserToken(null, email);

        log.info("User logged out successfully: {}", email);
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        // Lấy email từ JWT token
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ACCESS_TOKEN));

        // Kiểm tra mật khẩu mới và xác nhận mật khẩu có khớp không
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        log.info("Changing password for user: {}", email);

        // Gọi service để đổi mật khẩu
        userService.changePassword(email, request.getCurrentPassword(), request.getNewPassword());

        log.info("Password changed successfully for user: {}", email);
    }

    @Override
    public AuthResponse.UserInfo updateProfile(UpdateProfileRequest request) {
        // Lấy email từ JWT token
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ACCESS_TOKEN));

        log.info("Updating profile for user: {}", email);

        // Lấy user từ database
        User user = userService.getUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // Cập nhật thông tin
        user.setFullName(request.getFullName());
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        // Lưu vào database
        User updatedUser = userService.updateUser(user);

        log.info("Profile updated successfully for user: {}", email);

        return buildUserInfo(updatedUser);
    }

    // ── Firebase OAuth login ──────────────────────────────────────────────────

    @Override
    public AuthResponse firebaseLogin(String idToken) {
        log.info("Firebase login with ID token");
        try {
            FirebaseToken firebaseToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            String email = firebaseToken.getEmail();
            String name = firebaseToken.getName();
            String picture = firebaseToken.getPicture();
            String uid = firebaseToken.getUid();

            // Facebook có thể không trả email → dùng uid@facebook.local làm placeholder
            if (email == null || email.isBlank()) {
                email = uid + "@facebook.local";
            }

            // Xác định provider (Google / Facebook)
            String signInProvider = "unknown";
            Object firebaseClaim = firebaseToken.getClaims().get("firebase");
            if (firebaseClaim instanceof java.util.Map<?, ?> fbMap) {
                Object sp = fbMap.get("sign_in_provider");
                if (sp instanceof String s) signInProvider = s;
            }
            AuthProvider provider = signInProvider.contains("google")
                    ? AuthProvider.GOOGLE
                    : signInProvider.contains("facebook")
                    ? AuthProvider.FACEBOOK
                    : AuthProvider.LOCAL;

            // Tìm user bằng email (link account nếu đã có)
            User user = userService.getUserByEmail(email).orElse(null);

            if (user == null) {
                // Tạo account mới
                user = User.builder()
                        .email(email)
                        .fullName(name != null ? name : email.split("@")[0])
                        .passwordHash("") // OAuth user không có mật khẩu
                        .avatarUrl(picture)
                        .role(UserRole.USER)
                        .isActive(true)
                        .emailVerified(true) // Email từ Google/Facebook đã được xác thực
                        .authProvider(provider)
                        .providerId(uid)
                        .plan(planRepository.findByCode("FREE").orElse(null))
                        .build();
                user = userService.updateUser(user);
                log.info("Created new user from Firebase: {} ({})", email, provider);
            } else {
                // Link provider nếu chưa có
                if (user.getAuthProvider() == AuthProvider.LOCAL) {
                    user.setAuthProvider(provider);
                    user.setProviderId(uid);
                }
                // Cập nhật avatar nếu chưa có
                if (user.getAvatarUrl() == null && picture != null) {
                    user.setAvatarUrl(picture);
                }
                user.setEmailVerified(true);
                user = userService.updateUser(user);
                log.info("Linked Firebase provider for existing user: {} ({})", email, provider);
            }

            // Tạo JWT giống login thường
            AuthResponse.UserInfo userInfo = buildUserInfo(user);
            AuthResponse authResponse = new AuthResponse();
            authResponse.setUser(userInfo);

            String accessToken = securityUtil.createAccessToken(email, userInfo);
            String refreshToken = securityUtil.createRefreshToken(email, authResponse);

            user.setRefreshToken(refreshToken);
            userService.updateUser(user);

            authResponse.setAccessToken(accessToken);
            authResponse.setRefreshToken(refreshToken);
            authResponse.setTokenType("Bearer");

            return authResponse;
        } catch (Exception e) {
            log.error("Firebase login failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS,
                    "Xác thực Firebase thất bại: " + e.getMessage());
        }
    }

    private AuthResponse.UserInfo buildUserInfo(User user) {
        return AuthResponse.UserInfo.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .emailVerified(user.getEmailVerified())
                .authProvider(user.getAuthProvider() != null ? user.getAuthProvider().name() : "LOCAL")
                .build();
    }
}
