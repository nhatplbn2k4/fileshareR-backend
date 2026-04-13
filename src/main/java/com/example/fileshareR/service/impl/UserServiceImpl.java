package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.RegisterRequest;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.UserRole;
import com.example.fileshareR.repository.UserRepository;
import com.example.fileshareR.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public String createUser(RegisterRequest request) {
        log.info("Creating new user with email: {}", request.getEmail());

        if (existsByEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.EMAIL_EXISTED);
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(UserRole.USER)
                .isActive(true)
                .build();

        userRepository.save(user);
        log.info("User created successfully: {}", user.getEmail());

        return "Đăng ký thành công";
    }

    @Override
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public Optional<User> getUserById(Long userId) {
        return userRepository.findById(userId);
    }

    @Override
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    @Override
    public void updateUserToken(String token, String email) {
        User user = getUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.setRefreshToken(token);
        userRepository.save(user);
    }

    @Override
    public User getUserByRefreshTokenAndEmail(String token, String email) {
        User user = getUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.getRefreshToken() != null && user.getRefreshToken().equals(token)) {
            return user;
        }

        return null;
    }

    @Override
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = getUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new CustomException(ErrorCode.SAME_PASSWORD);
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed successfully for user: {}", email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}
