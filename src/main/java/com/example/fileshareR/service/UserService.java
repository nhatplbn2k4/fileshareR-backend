package com.example.fileshareR.service;

import com.example.fileshareR.dto.request.RegisterRequest;
import com.example.fileshareR.entity.User;

import java.util.Optional;

public interface UserService {
    String createUser(RegisterRequest request);
    Optional<User> getUserByEmail(String email);
    Optional<User> getUserById(Long userId);
    User updateUser(User user);
    void updateUserToken(String token, String email);
    User getUserByRefreshTokenAndEmail(String token, String email);
    void changePassword(String email, String currentPassword, String newPassword);
    boolean existsByEmail(String email);
}
