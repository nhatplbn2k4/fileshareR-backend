package com.example.fileshareR.repository;

import com.example.fileshareR.entity.ForgotPassword;
import com.example.fileshareR.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

@Repository
public interface ForgotPasswordRepository extends JpaRepository<ForgotPassword, Long> {

    @Query("SELECT fp FROM ForgotPassword fp WHERE fp.otp = ?1 AND fp.user = ?2")
    Optional<ForgotPassword> findByOtpAndUser(Integer otp, User user);

    @Query("SELECT fp FROM ForgotPassword fp WHERE fp.user = ?1 AND fp.isVerified = ?2")
    Optional<ForgotPassword> findByUserAndIsVerified(User user, Boolean isVerified);

    @Transactional
    @Modifying
    @Query("DELETE FROM ForgotPassword fp WHERE fp.user = ?1")
    void deleteByUser(User user);

    @Transactional
    @Modifying
    @Query("DELETE FROM ForgotPassword fp WHERE fp.expirationTime < ?1")
    void deleteExpiredOtps(Date now);
}
