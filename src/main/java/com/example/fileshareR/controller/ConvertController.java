package com.example.fileshareR.controller;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.response.DocumentResponse;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.service.PdfConvertService;
import com.example.fileshareR.service.UserService;
import com.example.fileshareR.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/convert")
@RequiredArgsConstructor
public class ConvertController {

    private final PdfConvertService pdfConvertService;
    private final UserService userService;

    /** Convert PDF upload → tải về DOCX trực tiếp. */
    @PostMapping(value = "/pdf-to-word/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> convertUploadDownload(@RequestParam("file") MultipartFile file) {
        Long userId = getCurrentUserId();
        byte[] docx = pdfConvertService.convertUploaded(file, userId);
        String name = pdfConvertService.generateDocxFileName(file.getOriginalFilename());
        return docxResponse(docx, name);
    }

    /** Convert PDF upload → lưu vào folder của user. */
    @PostMapping(value = "/pdf-to-word/upload/save", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> convertUploadSave(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) Long folderId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(pdfConvertService.convertUploadedAndSave(file, folderId, userId));
    }

    /** Convert document PDF có sẵn của user → tải về DOCX. */
    @PostMapping("/pdf-to-word/document/{documentId}")
    public ResponseEntity<byte[]> convertDocumentDownload(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        byte[] docx = pdfConvertService.convertFromDocument(documentId, userId);
        String name = "converted_" + documentId + ".docx";
        return docxResponse(docx, name);
    }

    /** Convert document PDF có sẵn → lưu vào folder. */
    @PostMapping("/pdf-to-word/document/{documentId}/save")
    public ResponseEntity<DocumentResponse> convertDocumentSave(
            @PathVariable Long documentId,
            @RequestParam(value = "folderId", required = false) Long folderId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(pdfConvertService.convertFromDocumentAndSave(documentId, folderId, userId));
    }

    /** Engine sẽ dùng cho user hiện tại (để FE hiển thị badge). */
    @GetMapping("/engine")
    public ResponseEntity<Map<String, String>> getCurrentEngine() {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(Map.of("engine", pdfConvertService.getEngineNameForUser(userId)));
    }

    private ResponseEntity<byte[]> docxResponse(byte[] docx, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setContentLength(docx.length);
        return new ResponseEntity<>(docx, headers, org.springframework.http.HttpStatus.OK);
    }

    private Long getCurrentUserId() {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ACCESS_TOKEN));
        User user = userService.getUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return user.getId();
    }
}
