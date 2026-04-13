package com.example.fileshareR.controller;

import com.example.fileshareR.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/otp")
@Slf4j
public class OtpController {

    private final OtpService otpService;

    // ==================== EMAIL VERIFICATION ====================

    @PostMapping("/email-verification/send")
    public ResponseEntity<String> sendEmailVerificationOtp() {
        String message = otpService.sendEmailVerificationOtp();
        return ResponseEntity.ok(message);
    }

    @PostMapping("/email-verification/send/{email}")
    public ResponseEntity<String> sendEmailVerificationOtpByEmail(@PathVariable String email) {
        String message = otpService.sendEmailVerificationOtp(email);
        return ResponseEntity.ok(message);
    }

    @PostMapping("/email-verification/verify/{otp}")
    public ResponseEntity<String> verifyEmailOtp(@PathVariable Integer otp) {
        String message = otpService.verifyEmailOtp(otp);
        return ResponseEntity.ok(message);
    }

    @PostMapping("/email-verification/verify/{email}/{otp}")
    public ResponseEntity<String> verifyEmailOtpByEmail(@PathVariable String email, @PathVariable Integer otp) {
        String message = otpService.verifyEmailOtp(email, otp);
        return ResponseEntity.ok(message);
    }

    // ==================== CHANGE PASSWORD OTP ====================

    @PostMapping("/change-password/send")
    public ResponseEntity<String> sendChangePasswordOtp() {
        String message = otpService.sendChangePasswordOtp();
        return ResponseEntity.ok(message);
    }

    @PostMapping("/change-password/verify/{otp}")
    public ResponseEntity<String> verifyChangePasswordOtp(@PathVariable Integer otp) {
        String message = otpService.verifyChangePasswordOtp(otp);
        return ResponseEntity.ok(message);
    }
}
