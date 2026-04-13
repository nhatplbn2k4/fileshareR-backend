package com.example.fileshareR.service;

import com.example.fileshareR.repository.ForgotPasswordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpCleanupService {

    private final ForgotPasswordRepository forgotPasswordRepository;

    /**
     * Tự động xóa các OTP đã hết hạn
     * Chạy mỗi 10 phút
     */
    @Scheduled(fixedRate = 600000) // 10 minutes
    @Transactional
    public void cleanupExpiredOtps() {
        try {
            Date now = new Date();
            forgotPasswordRepository.deleteExpiredOtps(now);
            log.debug("Cleaned up expired OTPs");
        } catch (Exception e) {
            log.error("Error cleaning up expired OTPs", e);
        }
    }
}
