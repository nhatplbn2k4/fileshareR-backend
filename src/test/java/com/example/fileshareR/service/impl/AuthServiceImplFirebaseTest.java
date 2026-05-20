package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.response.AuthResponse;
import com.example.fileshareR.entity.Plan;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.AuthProvider;
import com.example.fileshareR.enums.UserRole;
import com.example.fileshareR.repository.PlanRepository;
import com.example.fileshareR.service.OtpService;
import com.example.fileshareR.service.TokenBlacklistService;
import com.example.fileshareR.service.UserService;
import com.example.fileshareR.util.SecurityUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplFirebaseTest {

    @Mock private AuthenticationManagerBuilder authenticationManagerBuilder;
    @Mock private SecurityUtil securityUtil;
    @Mock private UserService userService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private OtpService otpService;
    @Mock private PlanRepository planRepository;
    @Mock private FirebaseAuth firebaseAuth;
    @Mock private FirebaseToken firebaseToken;

    private AuthServiceImpl service;
    private MockedStatic<FirebaseAuth> firebaseAuthStatic;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(authenticationManagerBuilder, securityUtil,
                userService, tokenBlacklistService, otpService, planRepository);
        firebaseAuthStatic = Mockito.mockStatic(FirebaseAuth.class);
        firebaseAuthStatic.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
    }

    @AfterEach
    void tearDown() {
        firebaseAuthStatic.close();
    }

    @Test
    void firebaseLogin_invalidToken_wrappedAsInvalidCredentials() throws FirebaseAuthException {
        when(firebaseAuth.verifyIdToken("bad-token")).thenThrow(
                new RuntimeException("bad signature"));

        assertThatThrownBy(() -> service.firebaseLogin("bad-token"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void firebaseLogin_newGoogleUser_createsAccount() throws FirebaseAuthException {
        when(firebaseAuth.verifyIdToken("good-tok")).thenReturn(firebaseToken);
        when(firebaseToken.getEmail()).thenReturn("alice@gmail.com");
        when(firebaseToken.getName()).thenReturn("Alice");
        when(firebaseToken.getPicture()).thenReturn("https://av/a.png");
        when(firebaseToken.getUid()).thenReturn("uid-123");
        when(firebaseToken.getClaims()).thenReturn(Map.of(
                "firebase", Map.of("sign_in_provider", "google.com")));

        when(userService.getUserByEmail("alice@gmail.com")).thenReturn(Optional.empty());
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(Plan.builder().code("FREE").build()));
        when(userService.updateUser(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(7L);
            return u;
        });
        when(securityUtil.createAccessToken(eq("alice@gmail.com"), any())).thenReturn("at");
        when(securityUtil.createRefreshToken(eq("alice@gmail.com"), any(AuthResponse.class))).thenReturn("rt");

        AuthResponse resp = service.firebaseLogin("good-tok");

        assertThat(resp.getAccessToken()).isEqualTo("at");
        assertThat(resp.getRefreshToken()).isEqualTo("rt");
        assertThat(resp.getUser().getEmail()).isEqualTo("alice@gmail.com");
        assertThat(resp.getUser().getAuthProvider()).isEqualTo("GOOGLE");
    }

    @Test
    void firebaseLogin_facebookNullEmail_synthesisesEmail() throws FirebaseAuthException {
        when(firebaseAuth.verifyIdToken("fb-tok")).thenReturn(firebaseToken);
        when(firebaseToken.getEmail()).thenReturn(null);
        when(firebaseToken.getName()).thenReturn("FbUser");
        when(firebaseToken.getUid()).thenReturn("fb-uid-99");
        when(firebaseToken.getClaims()).thenReturn(Map.of(
                "firebase", Map.of("sign_in_provider", "facebook.com")));

        when(userService.getUserByEmail("fb-uid-99@facebook.local")).thenReturn(Optional.empty());
        when(userService.updateUser(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(securityUtil.createAccessToken(any(), any())).thenReturn("at");
        when(securityUtil.createRefreshToken(any(), any(AuthResponse.class))).thenReturn("rt");

        AuthResponse resp = service.firebaseLogin("fb-tok");

        assertThat(resp.getUser().getEmail()).isEqualTo("fb-uid-99@facebook.local");
        assertThat(resp.getUser().getAuthProvider()).isEqualTo("FACEBOOK");
    }

    @Test
    void firebaseLogin_existingLocalUser_linksProvider() throws FirebaseAuthException {
        when(firebaseAuth.verifyIdToken("tok")).thenReturn(firebaseToken);
        when(firebaseToken.getEmail()).thenReturn("alice@x.com");
        when(firebaseToken.getUid()).thenReturn("uid-1");
        when(firebaseToken.getPicture()).thenReturn("new-pic");
        when(firebaseToken.getClaims()).thenReturn(Map.of(
                "firebase", Map.of("sign_in_provider", "google.com")));

        User existing = User.builder().id(1L).email("alice@x.com").fullName("A")
                .role(UserRole.USER).isActive(true)
                .authProvider(AuthProvider.LOCAL).avatarUrl(null).build();
        when(userService.getUserByEmail("alice@x.com")).thenReturn(Optional.of(existing));
        when(userService.updateUser(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(securityUtil.createAccessToken(any(), any())).thenReturn("at");
        when(securityUtil.createRefreshToken(any(), any(AuthResponse.class))).thenReturn("rt");

        service.firebaseLogin("tok");

        assertThat(existing.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(existing.getProviderId()).isEqualTo("uid-1");
        assertThat(existing.getAvatarUrl()).isEqualTo("new-pic");
        assertThat(existing.getEmailVerified()).isTrue();
    }

    @Test
    void firebaseLogin_inactiveUser_throws() throws FirebaseAuthException {
        when(firebaseAuth.verifyIdToken("tok")).thenReturn(firebaseToken);
        when(firebaseToken.getEmail()).thenReturn("blocked@x.com");
        when(firebaseToken.getUid()).thenReturn("uid-x");
        when(firebaseToken.getClaims()).thenReturn(Map.of(
                "firebase", Map.of("sign_in_provider", "google.com")));

        User blocked = User.builder().id(5L).email("blocked@x.com")
                .authProvider(AuthProvider.GOOGLE).isActive(false).build();
        when(userService.getUserByEmail("blocked@x.com")).thenReturn(Optional.of(blocked));
        when(userService.updateUser(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.firebaseLogin("tok"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);
    }

    @Test
    void firebaseLogin_unknownProvider_falsBackToLocal() throws FirebaseAuthException {
        when(firebaseAuth.verifyIdToken("tok")).thenReturn(firebaseToken);
        when(firebaseToken.getEmail()).thenReturn("u@x.com");
        when(firebaseToken.getName()).thenReturn(null);
        when(firebaseToken.getUid()).thenReturn("uid");
        // No "firebase" claim in map → signInProvider stays "unknown" → AuthProvider.LOCAL
        when(firebaseToken.getClaims()).thenReturn(Map.of());

        when(userService.getUserByEmail("u@x.com")).thenReturn(Optional.empty());
        when(userService.updateUser(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(securityUtil.createAccessToken(any(), any())).thenReturn("at");
        when(securityUtil.createRefreshToken(any(), any(AuthResponse.class))).thenReturn("rt");

        AuthResponse resp = service.firebaseLogin("tok");

        // No provider info → LOCAL fallback
        assertThat(resp.getUser().getAuthProvider()).isEqualTo("LOCAL");
        // fullName fallback to email-localpart
        assertThat(resp.getUser().getFullName()).isEqualTo("u");
    }
}
