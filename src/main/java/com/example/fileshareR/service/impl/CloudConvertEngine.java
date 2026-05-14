package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.service.PdfConvertEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * PREMIUM engine — CloudConvert API.
 * Flow: create job (import/upload → convert → export/url) → upload PDF →
 * poll job → download DOCX from export URL.
 */
@Service("cloudConvertEngine")
@RequiredArgsConstructor
@Slf4j
public class CloudConvertEngine implements PdfConvertEngine {

    @Value("${cloudconvert.api-key}")
    private String apiKey;

    @Value("${cloudconvert.api-url}")
    private String apiUrl;

    private final ObjectMapper objectMapper;

    private static final long POLL_INTERVAL_MS = 2000;
    private static final long POLL_TIMEOUT_MS = 5 * 60 * 1000; // 5 min

    @Override
    public String getEngineName() {
        return "cloudconvert";
    }

    @Override
    public byte[] convertPdfToWord(MultipartFile pdfFile) throws IOException {
        return convertPdfToWord(pdfFile.getBytes(), pdfFile.getOriginalFilename());
    }

    @Override
    public byte[] convertPdfToWord(byte[] pdfBytes, String originalName) throws IOException {
        RestClient client = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();

        // Step 1: create job
        Map<String, Object> tasks = new HashMap<>();
        tasks.put("import-my-file", Map.of("operation", "import/upload"));
        tasks.put("convert-my-file", Map.of(
                "operation", "convert",
                "input", "import-my-file",
                "output_format", "docx"));
        tasks.put("export-my-file", Map.of(
                "operation", "export/url",
                "input", "convert-my-file"));

        Map<String, Object> jobBody = Map.of(
                "tasks", tasks,
                "tag", "fileshareR-pdf-to-word");

        String jobResp;
        try {
            jobResp = client.post()
                    .uri("/jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jobBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            handleCloudConvertError(ex);
            throw ex; // unreachable, handleCloudConvertError throws
        }

        JsonNode jobNode = objectMapper.readTree(jobResp).path("data");
        String jobId = jobNode.path("id").asText();

        JsonNode uploadTask = findTaskByOperation(jobNode, "import/upload");
        if (uploadTask == null || uploadTask.path("result").isMissingNode()) {
            throw new IOException("CloudConvert: missing upload task result");
        }

        // Step 2: upload PDF — S3 yêu cầu (1) các param trước, file LAST;
        // (2) file part phải có Content-Type chính xác (application/pdf), Spring mặc định dùng
        // application/octet-stream nên phải wrap bằng HttpEntity + headers thủ công.
        JsonNode form = uploadTask.path("result").path("form");
        String uploadUrl = form.path("url").asText();
        JsonNode params = form.path("parameters");

        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = params.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            multipart.add(e.getKey(), e.getValue().asText());
        }

        String safeName = (originalName == null || originalName.isBlank()) ? "input.pdf" : originalName;
        ByteArrayResource fileRes = new ByteArrayResource(pdfBytes) {
            @Override public String getFilename() { return safeName; }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_PDF);
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(fileRes, fileHeaders);
        multipart.add("file", filePart);

        RestClient uploadClient = RestClient.create();
        uploadClient.post()
                .uri(uploadUrl)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart)
                .retrieve()
                .toBodilessEntity();

        // Step 3: poll job status
        long startTime = System.currentTimeMillis();
        JsonNode finalJob = null;

        while (System.currentTimeMillis() - startTime < POLL_TIMEOUT_MS) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("CloudConvert polling interrupted");
            }

            String statusResp = client.get()
                    .uri("/jobs/" + jobId)
                    .retrieve()
                    .body(String.class);

            JsonNode data = objectMapper.readTree(statusResp).path("data");
            String status = data.path("status").asText();

            if ("finished".equals(status)) {
                finalJob = data;
                break;
            }
            if ("error".equals(status)) {
                JsonNode errorTask = null;
                for (JsonNode t : data.path("tasks")) {
                    if ("error".equals(t.path("status").asText())) { errorTask = t; break; }
                }
                String msg = errorTask != null ? errorTask.path("message").asText("Convert failed") : "Convert failed";
                throw new IOException("CloudConvert error: " + msg);
            }
        }

        if (finalJob == null) {
            throw new IOException("CloudConvert: convert timeout");
        }

        // Step 4: download
        JsonNode exportTask = findTaskByOperation(finalJob, "export/url");
        if (exportTask == null) throw new IOException("CloudConvert: no export task");

        JsonNode files = exportTask.path("result").path("files");
        if (!files.isArray() || files.size() == 0) {
            throw new IOException("CloudConvert: empty export result");
        }
        String downloadUrl = files.get(0).path("url").asText();

        RestClient downloadClient = RestClient.create();
        ResponseEntity<byte[]> downloadResp = downloadClient.get()
                .uri(downloadUrl)
                .retrieve()
                .toEntity(byte[].class);

        byte[] body = downloadResp.getBody();
        if (body == null || body.length == 0) {
            throw new IOException("CloudConvert: empty downloaded file");
        }
        return body;
    }

    private JsonNode findTaskByOperation(JsonNode jobNode, String operation) {
        for (JsonNode t : jobNode.path("tasks")) {
            if (operation.equals(t.path("operation").asText())) return t;
        }
        return null;
    }

    private void handleCloudConvertError(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        String code = null;
        try {
            JsonNode node = objectMapper.readTree(body);
            code = node.path("code").asText(null);
        } catch (Exception ignored) {}

        log.error("CloudConvert API error [{}]: code={}, body={}",
                ex.getStatusCode(), code, body);

        if ("CREDITS_EXCEEDED".equals(code) || ex.getStatusCode().value() == 402) {
            throw new CustomException(ErrorCode.CONVERT_CREDITS_EXCEEDED);
        }
        if (ex.getStatusCode().is4xxClientError() && ex.getStatusCode().value() == 401) {
            throw new CustomException(ErrorCode.CONVERT_AUTH_FAILED);
        }
        throw new CustomException(ErrorCode.CONVERT_FAILED);
    }
}
