package com.example.fileshareR.controller;

import com.example.fileshareR.dto.request.ResetPasswordRequest;
import com.example.fileshareR.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/forgot-password")
@Slf4j
public class ForgotPasswordController {

    private final OtpService otpService;

    @PostMapping("/send-otp/{email}")
    public ResponseEntity<String> sendForgotPasswordOtp(@PathVariable String email) {
        String message = otpService.sendForgotPasswordOtp(email);
        return ResponseEntity.ok(message);
    }

    @PostMapping("/verify-otp/{email}/{otp}")
    public ResponseEntity<String> verifyForgotPasswordOtp(
            @PathVariable String email,
            @PathVariable Integer otp) {
        String message = otpService.verifyForgotPasswordOtp(email, otp);
        return ResponseEntity.ok(message);
    }

    @PostMapping("/reset-password/{email}")
    public ResponseEntity<String> resetPassword(
            @PathVariable String email,
            @Valid @RequestBody ResetPasswordRequest request) {
        String message = otpService.resetPassword(email, request);
        return ResponseEntity.ok(message);
    }
}
