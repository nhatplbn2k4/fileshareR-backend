package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.ResetPasswordRequest;
import com.example.fileshareR.entity.ForgotPassword;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.repository.ForgotPasswordRepository;
import com.example.fileshareR.repository.UserRepository;
import com.example.fileshareR.service.EmailService;
import com.example.fileshareR.util.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.thymeleaf.context.Context;

import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private ForgotPasswordRepository forgotPasswordRepository;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;

    private OtpServiceImpl service;
    private MockedStatic<SecurityUtil> securityUtilMock;

    @BeforeEach
    void setUp() {
        service = new OtpServiceImpl(userRepository, forgotPasswordRepository,
                emailService, passwordEncoder);
        securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilMock.close();
    }

    // ── sendEmailVerificationOtp (current-user variant) ─────────────────────

    @Test
    void sendEmailVerificationOtp_noAuth_throwsInvalidAccessToken() {
        securityUtilMock.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendEmailVerificationOtp())
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN);
    }

    @Test
    void sendEmailVerificationOtp_currentUser_delegatesToEmailVariant() {
        securityUtilMock.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.of("a@x.com"));
        User user = User.builder().email("a@x.com").fullName("A").emailVerified(false).build();
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));

        String result = service.sendEmailVerificationOtp();

        assertThat(result).contains("a***@x.com");
        verify(emailService).sendHtmlMessage(eq("a@x.com"), anyString(),
                eq("email-verification-otp"), any(Context.class));
        verify(forgotPasswordRepository).save(any(ForgotPassword.class));
    }

    // ── sendEmailVerificationOtp(String email) ──────────────────────────────

    @Test
    void sendEmailVerificationOtp_userNotFound_throws() {
        when(userRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendEmailVerificationOtp("ghost@x.com"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void sendEmailVerificationOtp_alreadyVerified_shortCircuits() {
        User u = User.builder().email("v@x.com").emailVerified(true).build();
        when(userRepository.findByEmail("v@x.com")).thenReturn(Optional.of(u));

        String r = service.sendEmailVerificationOtp("v@x.com");

        assertThat(r).contains("đã được xác thực");
        verify(emailService, never()).sendHtmlMessage(any(), any(), any(), any());
        verify(forgotPasswordRepository, never()).save(any());
    }

    @Test
    void sendEmailVerificationOtp_emailSenderThrows_wrappedAsEmailSendFailed() {
        User u = User.builder().email("e@x.com").fullName("E").emailVerified(false).build();
        when(userRepository.findByEmail("e@x.com")).thenReturn(Optional.of(u));
        doThrow(new RuntimeException("smtp down"))
                .when(emailService).sendHtmlMessage(any(), any(), any(), any());

        assertThatThrownBy(() -> service.sendEmailVerificationOtp("e@x.com"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
    }

    // ── verifyEmailOtp ──────────────────────────────────────────────────────

    @Test
    void verifyEmailOtp_noAuth_throwsInvalidAccessToken() {
        securityUtilMock.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyEmailOtp(123456))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN);
    }

    @Test
    void verifyEmailOtp_userMissing_throws() {
        when(userRepository.findByEmail("g@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyEmailOtp("g@x.com", 1))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void verifyEmailOtp_alreadyVerified_returnsShortCircuit() {
        User u = User.builder().email("v@x.com").emailVerified(true).build();
        when(userRepository.findByEmail("v@x.com")).thenReturn(Optional.of(u));

        assertThat(service.verifyEmailOtp("v@x.com", 1)).isEqualTo("Email đã được xác thực");
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmailOtp_unknownOtp_throws() {
        User u = User.builder().email("u@x.com").emailVerified(false).build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(u));
        when(forgotPasswordRepository.findByOtpAndUser(999, u)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyEmailOtp("u@x.com", 999))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.OTP_NOT_FOUND);
    }

    @Test
    void verifyEmailOtp_expired_throwsAndDeletes() {
        User u = User.builder().email("u@x.com").emailVerified(false).build();
        ForgotPassword fp = ForgotPassword.builder().otp(123)
                .expirationTime(new Date(System.currentTimeMillis() - 60_000)) // 1 min ago
                .user(u).build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(u));
        when(forgotPasswordRepository.findByOtpAndUser(123, u)).thenReturn(Optional.of(fp));

        assertThatThrownBy(() -> service.verifyEmailOtp("u@x.com", 123))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.OTP_EXPIRED);
        verify(forgotPasswordRepository).deleteByUser(u);
    }

    @Test
    void verifyEmailOtp_validOtp_marksVerifiedAndDeletes() {
        User u = User.builder().email("u@x.com").emailVerified(false).build();
        ForgotPassword fp = ForgotPassword.builder().otp(555)
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .user(u).build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(u));
        when(forgotPasswordRepository.findByOtpAndUser(555, u)).thenReturn(Optional.of(fp));

        String r = service.verifyEmailOtp("u@x.com", 555);

        assertThat(r).contains("Xác thực email thành công");
        assertThat(u.getEmailVerified()).isTrue();
        verify(userRepository).save(u);
        verify(forgotPasswordRepository).deleteByUser(u);
    }

    // ── sendForgotPasswordOtp / verifyForgotPasswordOtp ─────────────────────

    @Test
    void sendForgotPasswordOtp_userMissing_throwsInvalidEmail() {
        when(userRepository.findByEmail("g@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendForgotPasswordOtp("g@x.com"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_EMAIL);
    }

    @Test
    void sendForgotPasswordOtp_happy_sendsMailAndSaves() {
        User u = User.builder().email("u@x.com").fullName("U").build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(u));

        String r = service.sendForgotPasswordOtp("u@x.com");

        assertThat(r).contains("u***@x.com");
        verify(emailService).sendHtmlMessage(eq("u@x.com"), anyString(),
                eq("password-reset-otp"), any(Context.class));
        verify(forgotPasswordRepository).save(any(ForgotPassword.class));
    }

    @Test
    void sendForgotPasswordOtp_emailFails_wrapped() {
        User u = User.builder().email("u@x.com").fullName("U").build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(u));
        doThrow(new RuntimeException("err"))
                .when(emailService).sendHtmlMessage(any(), any(), any(), any());

        assertThatThrownBy(() -> service.sendForgotPasswordOtp("u@x.com"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
    }

    @Test
    void verifyForgotPasswordOtp_validOtp_marksVerified() {
        User u = User.builder().email("u@x.com").build();
        ForgotPassword fp = ForgotPassword.builder().otp(123).isVerified(false)
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .user(u).build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(u));
        when(forgotPasswordRepository.findByOtpAndUser(123, u)).thenReturn(Optional.of(fp));

        service.verifyForgotPasswordOtp("u@x.com", 123);

        assertThat(fp.getIsVerified()).isTrue();
        verify(forgotPasswordRepository).save(fp);
    }

    @Test
    void verifyForgotPasswordOtp_unknownOtp_throws() {
        User u = User.builder().email("u@x.com").build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(u));
        when(forgotPasswordRepository.findByOtpAndUser(1, u)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyForgotPasswordOtp("u@x.com", 1))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.OTP_NOT_FOUND);
    }

    @Test
    void verifyForgotPasswordOtp_expired_throws() {
        User u = User.builder().email("u@x.com").build();
        ForgotPassword fp = ForgotPassword.builder().otp(1)
                .expirationTime(new Date(System.currentTimeMillis() - 60_000))
                .user(u).build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(u));
        when(forgotPasswordRepository.findByOtpAndUser(1, u)).thenReturn(Optional.of(fp));

        assertThatThrownBy(() -> service.verifyForgotPasswordOtp("u@x.com", 1))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.OTP_EXPIRED);
        verify(forgotPasswordRepository).deleteByUser(u);
    }

    @Test
    void verifyForgotPasswordOtp_userMissing_throwsInvalidEmail() {
        when(userRepository.findByEmail("g@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyForgotPasswordOtp("g@x.com", 1))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_EMAIL);
    }

    // ── resetPassword ───────────────────────────────────────────────────────

    @Test
    void resetPassword_validVerifiedOtp_encodesNewPassword() {
        User u = User.builder().email("u@x.com").passwordHash("OLD").build();
        ForgotPassword fp = ForgotPassword.builder().isVerified(true)
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .user(u).build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(u));
        when(forgotPasswordRepository.findByUserAndIsVerified(u, true)).thenReturn(Optional.of(fp));
        when(passwordEncoder.encode("newP")).thenReturn("NEWHASH");

        ResetPasswordRequest req = new ResetPasswordRequest("newP", "newP");
        service.resetPassword("u@x.com", req);

        assertThat(u.getPasswordHash()).isEqualTo("NEWHASH");
        verify(userRepository).save(u);
        verify(forgotPasswordRepository).deleteByUser(u);
    }

    @Test
    void resetPassword_userMissing_throwsInvalidEmail() {
        when(userRepository.findByEmail("g@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("g@x.com",
                new ResetPasswordRequest("n", "n")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_EMAIL);
    }

    @Test
    void resetPassword_otpNotVerified_throws() {
        User u = User.builder().email("u@x.com").build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(u));
        when(forgotPasswordRepository.findByUserAndIsVerified(u, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("u@x.com",
                new ResetPasswordRequest("n", "n")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.OTP_NOT_VERIFIED);
    }

    @Test
    void resetPassword_otpExpired_throws() {
        User u = User.builder().email("u@x.com").build();
        ForgotPassword fp = ForgotPassword.builder().isVerified(true)
                .expirationTime(new Date(System.currentTimeMillis() - 60_000)).user(u).build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(u));
        when(forgotPasswordRepository.findByUserAndIsVerified(u, true)).thenReturn(Optional.of(fp));

        assertThatThrownBy(() -> service.resetPassword("u@x.com",
                new ResetPasswordRequest("n", "n")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.OTP_EXPIRED);
    }

    @Test
    void resetPassword_mismatchConfirm_throws() {
        User u = User.builder().email("u@x.com").build();
        ForgotPassword fp = ForgotPassword.builder().isVerified(true)
                .expirationTime(new Date(System.currentTimeMillis() + 60_000)).user(u).build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(u));
        when(forgotPasswordRepository.findByUserAndIsVerified(u, true)).thenReturn(Optional.of(fp));

        assertThatThrownBy(() -> service.resetPassword("u@x.com",
                new ResetPasswordRequest("a", "b")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_MISMATCH);
    }

    // ── sendChangePasswordOtp / verifyChangePasswordOtp ─────────────────────

    @Test
    void sendChangePasswordOtp_noAuth_throws() {
        securityUtilMock.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendChangePasswordOtp())
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN);
    }

    @Test
    void sendChangePasswordOtp_userMissing_throws() {
        securityUtilMock.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.of("g@x.com"));
        when(userRepository.findByEmail("g@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendChangePasswordOtp())
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void sendChangePasswordOtp_happy_sendsMail() {
        securityUtilMock.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.of("u@x.com"));
        User u = User.builder().email("u@x.com").fullName("U").build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(u));

        String r = service.sendChangePasswordOtp();

        assertThat(r).contains("u***@x.com");
        verify(emailService).sendHtmlMessage(eq("u@x.com"), anyString(),
                eq("password-change-otp"), any(Context.class));
    }

    @Test
    void sendChangePasswordOtp_emailFails_wrapped() {
        securityUtilMock.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.of("u@x.com"));
        User u = User.builder().email("u@x.com").fullName("U").build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(u));
        doThrow(new RuntimeException("smtp")).when(emailService)
                .sendHtmlMessage(any(), any(), any(), any());

        assertThatThrownBy(() -> service.sendChangePasswordOtp())
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
    }

    @Test
    void verifyChangePasswordOtp_noAuth_throws() {
        securityUtilMock.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyChangePasswordOtp(1))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN);
    }

    @Test
    void verifyChangePasswordOtp_userMissing_throws() {
        securityUtilMock.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.of("g@x.com"));
        when(userRepository.findByEmail("g@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyChangePasswordOtp(1))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void verifyChangePasswordOtp_expired_throws() {
        securityUtilMock.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.of("u@x.com"));
        User u = User.builder().email("u@x.com").build();
        ForgotPassword fp = ForgotPassword.builder().otp(123)
                .expirationTime(new Date(System.currentTimeMillis() - 60_000)).user(u).build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(u));
        when(forgotPasswordRepository.findByOtpAndUser(123, u)).thenReturn(Optional.of(fp));

        assertThatThrownBy(() -> service.verifyChangePasswordOtp(123))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.OTP_EXPIRED);
    }

    @Test
    void verifyChangePasswordOtp_otpNotFound_throws() {
        securityUtilMock.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.of("u@x.com"));
        User u = User.builder().email("u@x.com").build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(u));
        when(forgotPasswordRepository.findByOtpAndUser(1, u)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyChangePasswordOtp(1))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.OTP_NOT_FOUND);
    }

    @Test
    void verifyChangePasswordOtp_valid_marksVerified() {
        securityUtilMock.when(SecurityUtil::getCurrentUserLogin).thenReturn(Optional.of("u@x.com"));
        User u = User.builder().email("u@x.com").build();
        ForgotPassword fp = ForgotPassword.builder().otp(555).isVerified(false)
                .expirationTime(new Date(System.currentTimeMillis() + 60_000)).user(u).build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(u));
        when(forgotPasswordRepository.findByOtpAndUser(555, u)).thenReturn(Optional.of(fp));

        service.verifyChangePasswordOtp(555);

        assertThat(fp.getIsVerified()).isTrue();
        verify(forgotPasswordRepository).save(fp);
    }
}
