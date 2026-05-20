package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.ChangePasswordRequest;
import com.example.fileshareR.dto.request.LoginRequest;
import com.example.fileshareR.dto.request.RegisterRequest;
import com.example.fileshareR.dto.request.UpdateProfileRequest;
import com.example.fileshareR.dto.response.AuthResponse;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.UserRole;
import com.example.fileshareR.repository.PlanRepository;
import com.example.fileshareR.service.OtpService;
import com.example.fileshareR.service.TokenBlacklistService;
import com.example.fileshareR.service.UserService;
import com.example.fileshareR.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private AuthenticationManagerBuilder authenticationManagerBuilder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private SecurityUtil securityUtil;
    @Mock private UserService userService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private OtpService otpService;
    @Mock private PlanRepository planRepository;

    private AuthServiceImpl service;
    private MockedStatic<SecurityUtil> securityUtilStatic;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(authenticationManagerBuilder, securityUtil,
                userService, tokenBlacklistService, otpService, planRepository);
        securityUtilStatic = Mockito.mockStatic(SecurityUtil.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        securityUtilStatic.close();
        SecurityContextHolder.clearContext();
    }

    // ── register ────────────────────────────────────────────────────────────

    @Test
    void register_createsUserAndSendsVerificationOtp() {
        RegisterRequest req = RegisterRequest.builder()
                .email("a@x.com").password("p").fullName("A").build();
        when(userService.createUser(req)).thenReturn("Đăng ký thành công");

        String result = service.register(req);

        assertThat(result).contains("Đăng ký thành công")
                .contains("kiểm tra email để xác thực");
        verify(otpService).sendEmailVerificationOtp("a@x.com");
    }

    // ── login ───────────────────────────────────────────────────────────────

    @Test
    void login_happy_returnsTokens() {
        LoginRequest req = new LoginRequest("a@x.com", "pw");
        Authentication auth = new UsernamePasswordAuthenticationToken("a@x.com", "pw",
                Collections.emptyList());
        User user = activeVerifiedUser("a@x.com");

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userService.getUserByEmail("a@x.com")).thenReturn(Optional.of(user));
        when(securityUtil.createAccessToken(eq("a@x.com"), any())).thenReturn("access-tok");
        when(securityUtil.createRefreshToken(eq("a@x.com"), any())).thenReturn("refresh-tok");

        AuthResponse resp = service.login(req);

        assertThat(resp.getAccessToken()).isEqualTo("access-tok");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh-tok");
        assertThat(resp.getTokenType()).isEqualTo("Bearer");
        assertThat(resp.getUser().getEmail()).isEqualTo("a@x.com");
        verify(userService).updateUserToken("refresh-tok", "a@x.com");
    }

    @Test
    void login_userMissingAfterAuth_throwsUserNotFound() {
        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken("a@x.com", "p",
                        Collections.emptyList()));
        when(userService.getUserByEmail("a@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("a@x.com", "p")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void login_inactiveUser_throwsNotActive() {
        User u = activeVerifiedUser("a@x.com");
        u.setIsActive(false);
        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken("a@x.com", "p",
                        Collections.emptyList()));
        when(userService.getUserByEmail("a@x.com")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.login(new LoginRequest("a@x.com", "p")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);
    }

    @Test
    void login_emailNotVerified_throwsEmailNotVerified() {
        User u = activeVerifiedUser("a@x.com");
        u.setEmailVerified(false);
        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken("a@x.com", "p",
                        Collections.emptyList()));
        when(userService.getUserByEmail("a@x.com")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.login(new LoginRequest("a@x.com", "p")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    // ── getAccount ──────────────────────────────────────────────────────────

    @Test
    void getAccount_noAuth_throwsInvalidAccessToken() {
        securityUtilStatic.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAccount())
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN);
    }

    @Test
    void getAccount_userMissing_throws() {
        securityUtilStatic.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.of("a@x.com"));
        when(userService.getUserByEmail("a@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAccount())
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void getAccount_returnsUserInfo() {
        securityUtilStatic.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.of("a@x.com"));
        User u = activeVerifiedUser("a@x.com");
        when(userService.getUserByEmail("a@x.com")).thenReturn(Optional.of(u));

        AuthResponse.UserInfo info = service.getAccount();

        assertThat(info.getEmail()).isEqualTo("a@x.com");
        assertThat(info.getIsActive()).isTrue();
        assertThat(info.getEmailVerified()).isTrue();
        assertThat(info.getAuthProvider()).isEqualTo("LOCAL");
    }

    // ── refreshToken ────────────────────────────────────────────────────────

    @Test
    void refreshToken_null_throws() {
        assertThatThrownBy(() -> service.refreshToken(null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void refreshToken_userServiceReturnsNull_throwsInvalidRefreshToken() {
        Jwt jwt = buildJwt("a@x.com");
        when(securityUtil.checkValidRefreshToken("rt")).thenReturn(jwt);
        when(userService.getUserByRefreshTokenAndEmail("rt", "a@x.com")).thenReturn(null);

        assertThatThrownBy(() -> service.refreshToken("rt"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void refreshToken_inactiveUser_throwsNotActive() {
        Jwt jwt = buildJwt("a@x.com");
        User u = activeVerifiedUser("a@x.com");
        u.setIsActive(false);
        when(securityUtil.checkValidRefreshToken("rt")).thenReturn(jwt);
        when(userService.getUserByRefreshTokenAndEmail("rt", "a@x.com")).thenReturn(u);

        assertThatThrownBy(() -> service.refreshToken("rt"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);
    }

    @Test
    void refreshToken_happy_returnsNewTokens() {
        Jwt jwt = buildJwt("a@x.com");
        User u = activeVerifiedUser("a@x.com");
        when(securityUtil.checkValidRefreshToken("rt")).thenReturn(jwt);
        when(userService.getUserByRefreshTokenAndEmail("rt", "a@x.com")).thenReturn(u);
        when(securityUtil.createAccessToken(eq("a@x.com"), any())).thenReturn("new-at");
        when(securityUtil.createRefreshToken(eq("a@x.com"), any())).thenReturn("new-rt");

        AuthResponse resp = service.refreshToken("rt");

        assertThat(resp.getAccessToken()).isEqualTo("new-at");
        assertThat(resp.getRefreshToken()).isEqualTo("new-rt");
        verify(userService).updateUserToken("new-rt", "a@x.com");
    }

    // ── logout ──────────────────────────────────────────────────────────────

    @Test
    void logout_missingHeader_throws() {
        HttpServletRequest req = stubRequestWithAuthHeader(null);

        assertThatThrownBy(() -> service.logout(req))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN);
    }

    @Test
    void logout_nonBearerHeader_throws() {
        HttpServletRequest req = stubRequestWithAuthHeader("Token abc");

        assertThatThrownBy(() -> service.logout(req))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN);
    }

    @Test
    void logout_emptySubject_throws() {
        HttpServletRequest req = stubRequestWithAuthHeader("Bearer at");
        when(securityUtil.checkValidAccessToken("at")).thenReturn(buildJwt(""));

        assertThatThrownBy(() -> service.logout(req))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN);
    }

    @Test
    void logout_userMissing_throws() {
        HttpServletRequest req = stubRequestWithAuthHeader("Bearer at");
        when(securityUtil.checkValidAccessToken("at")).thenReturn(buildJwt("a@x.com"));
        when(userService.getUserByEmail("a@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.logout(req))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void logout_happy_blacklistsAndClearsRefreshToken() {
        HttpServletRequest req = stubRequestWithAuthHeader("Bearer at");
        when(securityUtil.checkValidAccessToken("at")).thenReturn(buildJwt("a@x.com"));
        when(userService.getUserByEmail("a@x.com")).thenReturn(Optional.of(activeVerifiedUser("a@x.com")));

        service.logout(req);

        verify(tokenBlacklistService).blacklistToken("at");
        verify(userService).updateUserToken(null, "a@x.com");
    }

    // ── changePassword ──────────────────────────────────────────────────────

    @Test
    void changePassword_noAuth_throws() {
        securityUtilStatic.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword(
                new ChangePasswordRequest("c", "n", "n")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN);
    }

    @Test
    void changePassword_confirmMismatch_throws() {
        securityUtilStatic.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.of("a@x.com"));

        assertThatThrownBy(() -> service.changePassword(
                new ChangePasswordRequest("c", "new1", "new2")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_MISMATCH);
        verify(userService, never()).changePassword(any(), any(), any());
    }

    @Test
    void changePassword_happy_delegatesToUserService() {
        securityUtilStatic.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.of("a@x.com"));
        ChangePasswordRequest req = new ChangePasswordRequest("c", "n", "n");

        service.changePassword(req);

        verify(userService).changePassword("a@x.com", "c", "n");
    }

    // ── updateProfile ───────────────────────────────────────────────────────

    @Test
    void updateProfile_noAuth_throws() {
        securityUtilStatic.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfile(
                new UpdateProfileRequest("N", "url")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN);
    }

    @Test
    void updateProfile_userMissing_throws() {
        securityUtilStatic.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.of("g@x.com"));
        when(userService.getUserByEmail("g@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfile(
                new UpdateProfileRequest("N", null)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void updateProfile_happy_persistsNewName() {
        securityUtilStatic.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.of("a@x.com"));
        User u = activeVerifiedUser("a@x.com");
        when(userService.getUserByEmail("a@x.com")).thenReturn(Optional.of(u));
        when(userService.updateUser(u)).thenReturn(u);

        AuthResponse.UserInfo info = service.updateProfile(
                new UpdateProfileRequest("New Name", "avatar.png"));

        assertThat(u.getFullName()).isEqualTo("New Name");
        assertThat(u.getAvatarUrl()).isEqualTo("avatar.png");
        assertThat(info.getFullName()).isEqualTo("New Name");
    }

    @Test
    void updateProfile_nullAvatar_keepsExisting() {
        securityUtilStatic.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.of("a@x.com"));
        User u = activeVerifiedUser("a@x.com");
        u.setAvatarUrl("existing.png");
        when(userService.getUserByEmail("a@x.com")).thenReturn(Optional.of(u));
        when(userService.updateUser(u)).thenReturn(u);

        service.updateProfile(new UpdateProfileRequest("New Name", null));

        assertThat(u.getAvatarUrl()).isEqualTo("existing.png");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static User activeVerifiedUser(String email) {
        return User.builder()
                .id(1L)
                .email(email)
                .fullName("Test User")
                .passwordHash("hash")
                .role(UserRole.USER)
                .isActive(true)
                .emailVerified(true)
                .build();
    }

    private static Jwt buildJwt(String subject) {
        Jwt.Builder b = Jwt.withTokenValue("token-value")
                .header("alg", "HS512")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(claims -> {});
        if (subject != null && !subject.isEmpty()) {
            b.subject(subject);
        }
        return b.build();
    }

    private static HttpServletRequest stubRequestWithAuthHeader(String value) {
        return new org.springframework.mock.web.MockHttpServletRequest() {
            @Override
            public String getHeader(String name) {
                if (HttpHeaders.AUTHORIZATION.equals(name)) return value;
                return super.getHeader(name);
            }
        };
    }
}
