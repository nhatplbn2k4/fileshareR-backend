package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers branches that don't require a live {@code FirebaseApp}: validation
 * rejects, blank-input early returns, and the catch-all in {@link
 * FirebaseStorageServiceImpl#fileExists(String)}. Happy paths (storeFile,
 * getFilePath, fileExists with valid blob) require Firebase Admin SDK
 * initialisation and live outside the unit-test scope.
 */
class FirebaseStorageServiceImplTest {

    private FirebaseStorageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FirebaseStorageServiceImpl();
        ReflectionTestUtils.setField(service, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(service, "allowedExtensionsStr", "pdf,docx,doc,txt");
    }

    @Test
    void storeFile_unsupportedExtension_throws() {
        MultipartFile png = new MockMultipartFile("file", "img.png", "image/png", new byte[]{1, 2});

        assertThatThrownBy(() -> service.storeFile(png, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    void deleteFile_null_isNoOp() {
        assertThatCode(() -> service.deleteFile(null)).doesNotThrowAnyException();
    }

    @Test
    void deleteFile_blank_isNoOp() {
        assertThatCode(() -> service.deleteFile("   ")).doesNotThrowAnyException();
    }

    @Test
    void deleteFile_anyValue_swallowsExceptionGracefully() {
        // No FirebaseApp initialised — getStorage() will throw. deleteFile catches
        // Exception and logs WARN; must not propagate.
        assertThatCode(() -> service.deleteFile("path/to/file.pdf"))
                .doesNotThrowAnyException();
    }

    @Test
    void fileExists_null_returnsFalse() {
        assertThat(service.fileExists(null)).isFalse();
    }

    @Test
    void fileExists_blank_returnsFalse() {
        assertThat(service.fileExists("   ")).isFalse();
    }

    @Test
    void fileExists_anyValue_returnsFalseOnFirebaseUninitialised() {
        // Firebase Admin not initialised → exception caught → false
        assertThat(service.fileExists("ghost.pdf")).isFalse();
    }
}
