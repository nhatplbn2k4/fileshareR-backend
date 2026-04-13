package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.ResetPasswordRequest;
import com.example.fileshareR.entity.ForgotPassword;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.repository.ForgotPasswordRepository;
import com.example.fileshareR.repository.UserRepository;
import com.example.fileshareR.service.EmailService;
import com.example.fileshareR.service.OtpService;
import com.example.fileshareR.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OtpServiceImpl implements OtpService {

    private final UserRepository userRepository;
    private final ForgotPasswordRepository forgotPasswordRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    private static final long OTP_VALIDITY_MINUTES = 5;

    // ==================== EMAIL VERIFICATION (OTP-BASED) ====================

    @Override
    public String sendEmailVerificationOtp() {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ACCESS_TOKEN));
        return sendEmailVerificationOtp(email);
    }

    @Override
    public String sendEmailVerificationOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            log.info("Email already verified for user: {}", user.getEmail());
            return "Email đã được xác thực trước đó";
        }

        Integer otp = generateOtp();

        // Delete old OTPs before creating new one
        forgotPasswordRepository.deleteByUser(user);

        ForgotPassword forgotPassword = ForgotPassword.builder()
                .otp(otp)
                .expirationTime(new Date(System.currentTimeMillis() + OTP_VALIDITY_MINUTES * 60 * 1000))
                .user(user)
                .build();

        try {
            Context context = new Context();
            context.setVariable("username", user.getFullName());
            context.setVariable("otp", otp);

            emailService.sendHtmlMessage(
                    user.getEmail(),
                    "FileShareR - Xác Thực Email",
                    "email-verification-otp",
                    context);

            forgotPasswordRepository.save(forgotPassword);
            log.info("Email verification OTP sent to user: {}", user.getEmail());
            return "Mã OTP đã được gửi đến email: " + maskEmail(user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send email verification OTP to user: {}", user.getEmail(), e);
            throw new CustomException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    @Override
    public String verifyEmailOtp(Integer otp) {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ACCESS_TOKEN));
        return verifyEmailOtp(email, otp);
    }

    @Override
    public String verifyEmailOtp(String email, Integer otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return "Email đã được xác thực";
        }

        ForgotPassword forgotPassword = forgotPasswordRepository
                .findByOtpAndUser(otp, user)
                .orElseThrow(() -> new CustomException(ErrorCode.OTP_NOT_FOUND));

        if (forgotPassword.getExpirationTime().before(Date.from(Instant.now()))) {
            forgotPasswordRepository.deleteByUser(user);
            throw new CustomException(ErrorCode.OTP_EXPIRED);
        }

        // Update email verified status
        user.setEmailVerified(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // Delete OTP after successful verification
        forgotPasswordRepository.deleteByUser(user);

        log.info("Email verified successfully for user: {}", user.getEmail());
        return "Xác thực email thành công!";
    }

    // ==================== FORGOT PASSWORD ====================

    @Override
    public String sendForgotPasswordOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_EMAIL));

        Integer otp = generateOtp();

        // Delete old OTPs before creating new one
        forgotPasswordRepository.deleteByUser(user);

        ForgotPassword forgotPassword = ForgotPassword.builder()
                .otp(otp)
                .expirationTime(new Date(System.currentTimeMillis() + OTP_VALIDITY_MINUTES * 60 * 1000))
                .user(user)
                .build();

        try {
            Context context = new Context();
            context.setVariable("username", user.getFullName());
            context.setVariable("otp", otp);

            emailService.sendHtmlMessage(
                    email,
                    "FileShareR - Đặt Lại Mật Khẩu",
                    "password-reset-otp",
                    context);

            forgotPasswordRepository.save(forgotPassword);
            log.info("Password reset OTP sent to: {}", email);
            return "Mã OTP đã được gửi đến email: " + maskEmail(email);
        } catch (Exception e) {
            log.error("Failed to send password reset OTP to: {}", email, e);
            throw new CustomException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    @Override
    public String verifyForgotPasswordOtp(String email, Integer otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_EMAIL));

        ForgotPassword forgotPassword = forgotPasswordRepository
                .findByOtpAndUser(otp, user)
                .orElseThrow(() -> new CustomException(ErrorCode.OTP_NOT_FOUND));

        if (forgotPassword.getExpirationTime().before(Date.from(Instant.now()))) {
            forgotPasswordRepository.deleteByUser(user);
            throw new CustomException(ErrorCode.OTP_EXPIRED);
        }

        // Mark OTP as verified
        forgotPassword.setIsVerified(true);
        forgotPasswordRepository.save(forgotPassword);

        log.info("Password reset OTP verified for: {}", email);
        return "Mã OTP hợp lệ! Bạn có thể đặt lại mật khẩu.";
    }

    @Override
    public String resetPassword(String email, ResetPasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_EMAIL));

        // Check if OTP was verified
        ForgotPassword forgotPassword = forgotPasswordRepository
                .findByUserAndIsVerified(user, true)
                .orElseThrow(() -> new CustomException(ErrorCode.OTP_NOT_VERIFIED));

        if (forgotPassword.getExpirationTime().before(Date.from(Instant.now()))) {
            forgotPasswordRepository.deleteByUser(user);
            throw new CustomException(ErrorCode.OTP_EXPIRED);
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        // Encode and save new password
        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        user.setPasswordHash(encodedPassword);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // Delete OTP after successful reset
        forgotPasswordRepository.deleteByUser(user);

        log.info("Password reset successful for: {}", email);
        return "Đặt lại mật khẩu thành công!";
    }

    // ==================== CHANGE PASSWORD WITH OTP ====================

    @Override
    public String sendChangePasswordOtp() {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ACCESS_TOKEN));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Integer otp = generateOtp();

        // Delete old OTPs before creating new one
        forgotPasswordRepository.deleteByUser(user);

        ForgotPassword forgotPassword = ForgotPassword.builder()
                .otp(otp)
                .expirationTime(new Date(System.currentTimeMillis() + OTP_VALIDITY_MINUTES * 60 * 1000))
                .user(user)
                .build();

        try {
            Context context = new Context();
            context.setVariable("username", user.getFullName());
            context.setVariable("otp", otp);

            emailService.sendHtmlMessage(
                    user.getEmail(),
                    "FileShareR - Xác Nhận Đổi Mật Khẩu",
                    "password-change-otp",
                    context);

            forgotPasswordRepository.save(forgotPassword);
            log.info("Password change OTP sent to user: {}", user.getEmail());
            return "Mã OTP đã được gửi đến email: " + maskEmail(user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send password change OTP to user: {}", user.getEmail(), e);
            throw new CustomException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    @Override
    public String verifyChangePasswordOtp(Integer otp) {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ACCESS_TOKEN));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ForgotPassword forgotPassword = forgotPasswordRepository
                .findByOtpAndUser(otp, user)
                .orElseThrow(() -> new CustomException(ErrorCode.OTP_NOT_FOUND));

        if (forgotPassword.getExpirationTime().before(Date.from(Instant.now()))) {
            forgotPasswordRepository.deleteByUser(user);
            throw new CustomException(ErrorCode.OTP_EXPIRED);
        }

        // Mark OTP as verified
        forgotPassword.setIsVerified(true);
        forgotPasswordRepository.save(forgotPassword);

        log.info("Password change OTP verified for user: {}", user.getEmail());
        return "Mã OTP hợp lệ! Bạn có thể đổi mật khẩu.";
    }

    // ==================== HELPER METHODS ====================

    private Integer generateOtp() {
        Random random = new Random();
        return random.nextInt(100_000, 999_999);
    }

    private String maskEmail(String email) {
        String[] parts = email.split("@");
        if (parts.length != 2)
            return email;

        String localPart = parts[0];
        String domain = parts[1];

        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "***@" + domain;
        }

        return localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1) + "@" + domain;
    }
}
