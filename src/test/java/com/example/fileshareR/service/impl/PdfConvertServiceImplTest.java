package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.CreateDocumentRequest;
import com.example.fileshareR.dto.response.DocumentResponse;
import com.example.fileshareR.entity.Document;
import com.example.fileshareR.entity.Plan;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.FileType;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.UserRepository;
import com.example.fileshareR.service.DocumentService;
import com.example.fileshareR.service.FileStorageService;
import com.example.fileshareR.service.PdfConvertEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfConvertServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private DocumentService documentService;
    @Mock private PdfConvertEngine localEngine;
    @Mock private PdfConvertEngine cloudEngine;

    private PdfConvertServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PdfConvertServiceImpl(userRepository, documentRepository,
                fileStorageService, documentService, localEngine, cloudEngine);
    }

    // ── getEngineNameForUser ────────────────────────────────────────────────

    @Test
    void getEngineNameForUser_freeUser_returnsLocalEngineName() {
        User u = User.builder().id(1L).plan(Plan.builder().code("FREE").build()).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(localEngine.getEngineName()).thenReturn("local");

        assertThat(service.getEngineNameForUser(1L)).isEqualTo("local");
    }

    @Test
    void getEngineNameForUser_premiumUser_returnsCloudEngineName() {
        User u = User.builder().id(1L).plan(Plan.builder().code("PREMIUM").build()).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(cloudEngine.getEngineName()).thenReturn("cloud");

        assertThat(service.getEngineNameForUser(1L)).isEqualTo("cloud");
    }

    @Test
    void getEngineNameForUser_nullPlanUser_returnsLocalEngineName() {
        User u = User.builder().id(1L).plan(null).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(localEngine.getEngineName()).thenReturn("local");

        assertThat(service.getEngineNameForUser(1L)).isEqualTo("local");
    }

    @Test
    void getEngineNameForUser_missingUser_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEngineNameForUser(99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── convertUploaded ─────────────────────────────────────────────────────

    @Test
    void convertUploaded_notPdf_throws() {
        MultipartFile img = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> service.convertUploaded(img, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONVERT_PDF_ONLY);
    }

    @Test
    void convertUploaded_pdfByContentType_acceptedAndRoutedToLocalForFree() throws IOException {
        MultipartFile pdf = new MockMultipartFile("file", "doc", "application/pdf", "%PDF-x".getBytes());
        User u = User.builder().id(1L).plan(Plan.builder().code("FREE").build()).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(localEngine.convertPdfToWord(any(MultipartFile.class))).thenReturn(new byte[]{42});

        byte[] result = service.convertUploaded(pdf, 1L);

        assertThat(result).containsExactly((byte) 42);
        verify(cloudEngine, never()).convertPdfToWord(any(MultipartFile.class));
    }

    @Test
    void convertUploaded_pdfByExtension_accepted() throws IOException {
        MultipartFile pdf = new MockMultipartFile("file", "doc.PDF", null, "%PDF-x".getBytes());
        User u = User.builder().id(1L).plan(Plan.builder().code("FREE").build()).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(localEngine.convertPdfToWord(any(MultipartFile.class))).thenReturn(new byte[]{7});

        assertThat(service.convertUploaded(pdf, 1L)).containsExactly((byte) 7);
    }

    @Test
    void convertUploaded_engineIoError_wrappedAsConvertFailed() throws IOException {
        MultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF".getBytes());
        User u = User.builder().id(1L).plan(Plan.builder().code("FREE").build()).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(localEngine.convertPdfToWord(any(MultipartFile.class)))
                .thenThrow(new IOException("python missing"));

        assertThatThrownBy(() -> service.convertUploaded(pdf, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONVERT_FAILED);
    }

    // ── runWithFallback (cloud → local) ─────────────────────────────────────

    @Test
    void convertUploaded_premiumCloudCreditExhausted_fallsBackToLocal() throws IOException {
        MultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF".getBytes());
        User u = User.builder().id(1L).plan(Plan.builder().code("PREMIUM").build()).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(cloudEngine.convertPdfToWord(any(MultipartFile.class)))
                .thenThrow(new CustomException(ErrorCode.CONVERT_CREDITS_EXCEEDED));
        when(localEngine.convertPdfToWord(any(MultipartFile.class))).thenReturn(new byte[]{1, 2, 3});

        byte[] result = service.convertUploaded(pdf, 1L);

        assertThat(result).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    @Test
    void convertUploaded_premiumCloudOtherError_propagates() throws IOException {
        MultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF".getBytes());
        User u = User.builder().id(1L).plan(Plan.builder().code("PREMIUM").build()).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(cloudEngine.convertPdfToWord(any(MultipartFile.class)))
                .thenThrow(new CustomException(ErrorCode.CONVERT_FAILED));

        assertThatThrownBy(() -> service.convertUploaded(pdf, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONVERT_FAILED);
        verify(localEngine, never()).convertPdfToWord(any(MultipartFile.class));
    }

    // ── convertFromDocument ─────────────────────────────────────────────────

    @Test
    void convertFromDocument_documentNotFound_throws() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.convertFromDocument(99L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND);
    }

    @Test
    void convertFromDocument_notOwner_throws() {
        Document d = Document.builder().id(7L)
                .user(User.builder().id(99L).build())
                .fileType(FileType.PDF).build();
        when(documentRepository.findById(7L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.convertFromDocument(7L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_ACCESS_DENIED);
    }

    @Test
    void convertFromDocument_nonPdfFileType_throws() {
        Document d = Document.builder().id(7L)
                .user(User.builder().id(1L).build())
                .fileType(FileType.DOCX).build();
        when(documentRepository.findById(7L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.convertFromDocument(7L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONVERT_PDF_ONLY);
    }

    @Test
    void convertFromDocument_happy(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Path pdfPath = tmp.resolve("source.pdf");
        Files.write(pdfPath, "%PDF-1.4".getBytes());
        User u = User.builder().id(1L).plan(Plan.builder().code("FREE").build()).build();
        Document d = Document.builder().id(7L).user(u)
                .fileType(FileType.PDF).fileUrl("source.pdf")
                .fileName("source.pdf").build();

        when(documentRepository.findById(7L)).thenReturn(Optional.of(d));
        when(fileStorageService.getFilePath("source.pdf")).thenReturn(pdfPath);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(localEngine.convertPdfToWord(any(byte[].class), any(String.class)))
                .thenReturn(new byte[]{99});

        byte[] out = service.convertFromDocument(7L, 1L);

        assertThat(out).containsExactly((byte) 99);
    }

    // ── generateDocxFileName ────────────────────────────────────────────────

    @Test
    void generateDocxFileName_replacesPdfExtension() {
        assertThat(service.generateDocxFileName("report.pdf")).isEqualTo("report.docx");
        assertThat(service.generateDocxFileName("report.PDF")).isEqualTo("report.docx");
    }

    @Test
    void generateDocxFileName_nullOrEmpty_returnsDefault() {
        assertThat(service.generateDocxFileName(null)).isEqualTo("converted_document.docx");
        assertThat(service.generateDocxFileName("")).isEqualTo("converted_document.docx");
    }

    @Test
    void generateDocxFileName_noPdfExtension_appendsDocx() {
        assertThat(service.generateDocxFileName("report.txt")).isEqualTo("report.txt.docx");
    }

    // ── convertUploadedAndSave (full pipe) ──────────────────────────────────

    @Test
    void convertUploadedAndSave_delegatesToDocumentService() throws IOException {
        MultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF".getBytes());
        User u = User.builder().id(1L).plan(Plan.builder().code("FREE").build()).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(localEngine.convertPdfToWord(any(MultipartFile.class))).thenReturn(new byte[]{1, 2});
        DocumentResponse resp = DocumentResponse.builder().id(7L).title("doc").build();
        when(documentService.uploadDocument(any(), any(CreateDocumentRequest.class), anyLong()))
                .thenReturn(resp);

        DocumentResponse out = service.convertUploadedAndSave(pdf, 10L, 1L);

        assertThat(out).isSameAs(resp);
        ArgumentCaptor<CreateDocumentRequest> reqCap = ArgumentCaptor.forClass(CreateDocumentRequest.class);
        verify(documentService).uploadDocument(any(), reqCap.capture(), anyLong());
        assertThat(reqCap.getValue().getTitle()).isEqualTo("doc");
        assertThat(reqCap.getValue().getFolderId()).isEqualTo(10L);
    }
}
