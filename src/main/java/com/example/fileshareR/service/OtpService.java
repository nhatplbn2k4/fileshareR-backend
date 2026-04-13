package com.example.fileshareR.service;

import com.example.fileshareR.dto.request.ResetPasswordRequest;

public interface OtpService {
    // Email verification (OTP-based)
    String sendEmailVerificationOtp();

    String sendEmailVerificationOtp(String email); // For registration flow (no JWT)

    String verifyEmailOtp(Integer otp);

    String verifyEmailOtp(String email, Integer otp); // For registration flow (no JWT)

    // Forgot password
    String sendForgotPasswordOtp(String email);

    String verifyForgotPasswordOtp(String email, Integer otp);

    String resetPassword(String email, ResetPasswordRequest request);

    // Change password with OTP
    String sendChangePasswordOtp();

    String verifyChangePasswordOtp(Integer otp);
}
