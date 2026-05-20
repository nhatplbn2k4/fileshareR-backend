package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.RegisterRequest;
import com.example.fileshareR.entity.Plan;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.repository.PlanRepository;
import com.example.fileshareR.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PlanRepository planRepository;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, passwordEncoder, planRepository);
    }

    // ── createUser ──────────────────────────────────────────────────────────

    @Test
    void createUser_persistsHashedPassword_andAssignsFreePlan() {
        RegisterRequest req = RegisterRequest.builder()
                .email("alice@example.com")
                .password("plaintext")
                .fullName("Alice")
                .build();
        Plan freePlan = Plan.builder().id(1L).code("FREE").build();
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plaintext")).thenReturn("HASHED");
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));

        String result = service.createUser(req);

        assertThat(result).isEqualTo("Đăng ký thành công");
        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCap.capture());
        User saved = userCap.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("HASHED");
        assertThat(saved.getFullName()).isEqualTo("Alice");
        assertThat(saved.getIsActive()).isTrue();
        assertThat(saved.getPlan()).isEqualTo(freePlan);
    }

    @Test
    void createUser_emailAlreadyExists_throwsEmailExisted() {
        RegisterRequest req = RegisterRequest.builder().email("dup@x.com").password("p").fullName("D").build();
        when(userRepository.existsByEmail("dup@x.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_EXISTED);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_freePlanMissing_stillPersistsWithNullPlan() {
        RegisterRequest req = RegisterRequest.builder().email("noplan@x.com").password("p").fullName("N").build();
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("H");
        when(planRepository.findByCode("FREE")).thenReturn(Optional.empty());

        service.createUser(req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getPlan()).isNull();
    }

    // ── getUserByEmail / getUserById / updateUser ───────────────────────────

    @Test
    void getUserByEmail_delegatesToRepository() {
        User user = User.builder().id(7L).email("a@x.com").build();
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));

        assertThat(service.getUserByEmail("a@x.com")).contains(user);
    }

    @Test
    void getUserById_delegatesToRepository() {
        User user = User.builder().id(42L).build();
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        assertThat(service.getUserById(42L)).contains(user);
    }

    @Test
    void updateUser_delegatesToRepositorySave() {
        User user = User.builder().id(1L).build();
        when(userRepository.save(user)).thenReturn(user);

        assertThat(service.updateUser(user)).isSameAs(user);
    }

    // ── updateUserToken ─────────────────────────────────────────────────────

    @Test
    void updateUserToken_setsTokenAndSaves() {
        User user = User.builder().id(1L).email("a@x.com").build();
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));

        service.updateUserToken("rt-abc", "a@x.com");

        assertThat(user.getRefreshToken()).isEqualTo("rt-abc");
        verify(userRepository).save(user);
    }

    @Test
    void updateUserToken_userMissing_throwsUserNotFound() {
        when(userRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUserToken("rt", "ghost@x.com"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── getUserByRefreshTokenAndEmail ───────────────────────────────────────

    @Test
    void getUserByRefreshTokenAndEmail_matchingToken_returnsUser() {
        User user = User.builder().email("a@x.com").refreshToken("rt-1").build();
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));

        assertThat(service.getUserByRefreshTokenAndEmail("rt-1", "a@x.com")).isSameAs(user);
    }

    @Test
    void getUserByRefreshTokenAndEmail_tokenMismatch_returnsNull() {
        User user = User.builder().email("a@x.com").refreshToken("rt-stored").build();
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));

        assertThat(service.getUserByRefreshTokenAndEmail("rt-other", "a@x.com")).isNull();
    }

    @Test
    void getUserByRefreshTokenAndEmail_userMissing_throwsUserNotFound() {
        when(userRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserByRefreshTokenAndEmail("rt", "ghost@x.com"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── changePassword ──────────────────────────────────────────────────────

    @Test
    void changePassword_correctCurrent_andNewDifferent_savesEncoded() {
        User user = User.builder().email("a@x.com").passwordHash("OLD_HASH").build();
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("currPlain", "OLD_HASH")).thenReturn(true);
        when(passwordEncoder.matches("newPlain", "OLD_HASH")).thenReturn(false);
        when(passwordEncoder.encode("newPlain")).thenReturn("NEW_HASH");

        service.changePassword("a@x.com", "currPlain", "newPlain");

        assertThat(user.getPasswordHash()).isEqualTo("NEW_HASH");
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_userMissing_throwsUserNotFound() {
        when(userRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword("ghost@x.com", "c", "n"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void changePassword_currentPasswordWrong_throwsInvalidCurrentPassword() {
        User user = User.builder().email("a@x.com").passwordHash("H").build();
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "H")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword("a@x.com", "wrong", "n"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD);
    }

    @Test
    void changePassword_newSameAsOld_throwsSamePassword() {
        User user = User.builder().email("a@x.com").passwordHash("H").build();
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("curr", "H")).thenReturn(true);
        when(passwordEncoder.matches("curr", "H")).thenReturn(true);
        // newPassword equals current → matches "H" too
        when(passwordEncoder.matches("curr", "H")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword("a@x.com", "curr", "curr"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.SAME_PASSWORD);
    }

    // ── existsByEmail ───────────────────────────────────────────────────────

    @Test
    void existsByEmail_delegatesToRepository() {
        when(userRepository.existsByEmail("yes@x.com")).thenReturn(true);
        when(userRepository.existsByEmail("no@x.com")).thenReturn(false);

        assertThat(service.existsByEmail("yes@x.com")).isTrue();
        assertThat(service.existsByEmail("no@x.com")).isFalse();
    }
}
