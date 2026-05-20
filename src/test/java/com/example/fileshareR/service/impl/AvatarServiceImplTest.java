package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers validation branches in {@link AvatarServiceImpl#uploadAvatar}. The
 * happy path invokes {@code StorageClient.getInstance(FirebaseApp.getInstance())}
 * which requires a configured FirebaseApp — covered separately via integration
 * or with {@code MockedStatic}. Here we exercise input validation only.
 */
class AvatarServiceImplTest {

    private AvatarServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AvatarServiceImpl();
        ReflectionTestUtils.setField(service, "bucketName", "test-bucket");
    }

    @Test
    void uploadAvatar_emptyFile_throwsBadRequest() {
        MultipartFile empty = new MockMultipartFile("avatar", "a.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.uploadAvatar(empty, "users/1/avatar.png"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void uploadAvatar_unsupportedMime_throwsBadRequest() {
        MultipartFile pdf = new MockMultipartFile("avatar", "a.pdf",
                "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.uploadAvatar(pdf, "users/1/avatar.pdf"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void uploadAvatar_oversize_throwsBadRequest() {
        // 11 MB > MAX_SIZE (10 MB)
        byte[] big = new byte[11 * 1024 * 1024];
        MultipartFile huge = new MockMultipartFile("avatar", "big.png", "image/png", big);

        assertThatThrownBy(() -> service.uploadAvatar(huge, "users/1/big.png"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void uploadAvatar_jpegAccepted_passesValidation() {
        // Validation passes — actual upload fails because FirebaseApp not initialised
        // in test JVM. We assert the failure comes AFTER validation (i.e. some
        // RuntimeException from FirebaseApp.getInstance lookup), not a BAD_REQUEST.
        MultipartFile jpeg = new MockMultipartFile("avatar", "a.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});

        assertThatThrownBy(() -> service.uploadAvatar(jpeg, "users/1/a.jpg"))
                .isInstanceOf(Throwable.class) // FirebaseApp lookup or IO — not validation
                .satisfies(t -> {
                    if (t instanceof CustomException ce) {
                        // If it surfaces as CustomException, must not be a validation BAD_REQUEST
                        // with the validation messages — INTERNAL_SERVER_ERROR is acceptable.
                        if (ce.getErrorCode() == ErrorCode.BAD_REQUEST) {
                            throw new AssertionError(
                                    "Validation should have passed for valid JPEG, got BAD_REQUEST");
                        }
                    }
                });
    }
}
