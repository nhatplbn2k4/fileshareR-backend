package com.example.fileshareR.service.impl;

import com.example.fileshareR.service.PdfConvertEngine;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * FREE engine — LibreOffice (special pages) + pdf2docx (normal pages) + docxcompose merge.
 * Requires Python 3 + LibreOffice installed on host.
 */
@Service("localPdfConvertEngine")
public class LocalPdfConvertEngine implements PdfConvertEngine {

    private static final String[] LIBREOFFICE_PATHS = {
            "C:/Program Files/LibreOffice/program/soffice.exe",
            "C:/Program Files (x86)/LibreOffice/program/soffice.exe",
            "/usr/bin/soffice",
            "/usr/bin/libreoffice",
            "/Applications/LibreOffice.app/Contents/MacOS/soffice"
    };

    private static final Pattern TOC_PATTERN = Pattern.compile("\\.{5,}\\s*\\d+");
    private static final Pattern COVER_PATTERN = Pattern
            .compile("(?i)(ĐỒ ÁN|LUẬN VĂN|KHÓA LUẬN|BÁO CÁO|TRƯỜNG|KHOA|BỘ GIÁO DỤC)");

    @Override
    public String getEngineName() {
        return "local";
    }

    @Override
    public byte[] convertPdfToWord(MultipartFile pdfFile) throws IOException {
        return convertPdfToWord(pdfFile.getBytes(), pdfFile.getOriginalFilename());
    }

    @Override
    public byte[] convertPdfToWord(byte[] pdfBytes, String originalName) throws IOException {
        Map<String, List<Integer>> pageGroups = analyzePages(pdfBytes);

        List<Integer> specialPages = pageGroups.get("special");
        List<Integer> normalPages = pageGroups.get("normal");

        if (specialPages.isEmpty()) return convertWithPdf2Docx(pdfBytes);
        if (normalPages.isEmpty()) return convertWithLibreOffice(pdfBytes);
        return convertHybrid(pdfBytes, specialPages, normalPages);
    }

    private Map<String, List<Integer>> analyzePages(byte[] pdfBytes) {
        List<Integer> specialPages = new ArrayList<>();
        List<Integer> normalPages = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int totalPages = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();

            for (int i = 1; i <= totalPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(document);

                boolean isSpecial = false;
                if (TOC_PATTERN.matcher(text).find()) isSpecial = true;
                if (i <= 2 && COVER_PATTERN.matcher(text).find()) isSpecial = true;

                if (isSpecial) specialPages.add(i);
                else normalPages.add(i);
            }
        } catch (Exception e) {
            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                for (int i = 1; i <= document.getNumberOfPages(); i++) normalPages.add(i);
            } catch (Exception ex) {
                normalPages.add(1);
            }
        }

        Map<String, List<Integer>> result = new HashMap<>();
        result.put("special", specialPages);
        result.put("normal", normalPages);
        return result;
    }

    private byte[] convertHybrid(byte[] pdfBytes, List<Integer> specialPages, List<Integer> normalPages)
            throws IOException {
        Path tempDir = Files.createTempDirectory("pdf-hybrid-");
        try {
            List<PageSegment> segments = createSegments(specialPages, normalPages);

            List<Path> docxFiles = new ArrayList<>();
            List<Boolean> isSpecialList = new ArrayList<>();

            for (int i = 0; i < segments.size(); i++) {
                PageSegment segment = segments.get(i);

                Path segmentPdf = tempDir.resolve("segment_" + i + ".pdf");
                splitPdf(pdfBytes, segment.pages, segmentPdf);

                Path segmentDocx = tempDir.resolve("segment_" + i + ".docx");
                byte[] result = segment.isSpecial
                        ? convertWithLibreOffice(Files.readAllBytes(segmentPdf))
                        : convertWithPdf2Docx(Files.readAllBytes(segmentPdf));

                Files.write(segmentDocx, result);
                docxFiles.add(segmentDocx);
                isSpecialList.add(segment.isSpecial);
            }

            return mergeAllSegments(docxFiles, isSpecialList, tempDir);
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private List<PageSegment> createSegments(List<Integer> specialPages, List<Integer> normalPages) {
        Set<Integer> specialSet = new HashSet<>(specialPages);

        int maxPage = Math.max(
                specialPages.isEmpty() ? 0 : Collections.max(specialPages),
                normalPages.isEmpty() ? 0 : Collections.max(normalPages));

        List<PageSegment> segments = new ArrayList<>();
        List<Integer> currentPages = new ArrayList<>();
        Boolean currentType = null;

        for (int page = 1; page <= maxPage; page++) {
            boolean isSpecial = specialSet.contains(page);
            boolean isNormal = normalPages.contains(page);
            if (!isSpecial && !isNormal) continue;

            if (currentType == null) {
                currentType = isSpecial;
                currentPages.add(page);
            } else if (currentType == isSpecial) {
                currentPages.add(page);
            } else {
                segments.add(new PageSegment(new ArrayList<>(currentPages), currentType));
                currentPages.clear();
                currentType = isSpecial;
                currentPages.add(page);
            }
        }

        if (!currentPages.isEmpty()) segments.add(new PageSegment(currentPages, currentType));
        return segments;
    }

    private static class PageSegment {
        List<Integer> pages;
        boolean isSpecial;
        PageSegment(List<Integer> pages, boolean isSpecial) {
            this.pages = pages;
            this.isSpecial = isSpecial;
        }
    }

    private byte[] mergeAllSegments(List<Path> docxFiles, List<Boolean> isSpecialList, Path tempDir)
            throws IOException {
        if (docxFiles.isEmpty()) throw new IOException("No documents to merge");
        if (docxFiles.size() == 1) return Files.readAllBytes(docxFiles.get(0));

        Path outputPath = tempDir.resolve("python_merged.docx");

        List<String> command = new ArrayList<>();
        command.add(findPython());
        command.add(Paths.get("scripts", "merge_docx.py").toAbsolutePath().toString());
        command.add(outputPath.toString());

        for (int i = 0; i < docxFiles.size(); i++) {
            command.add(docxFiles.get(i).toString() + ":" + (isSpecialList.get(i) ? "1" : "0"));
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // drain output
            }
        }

        try {
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                throw new IOException("Python merge timeout");
            }
            if (process.exitValue() != 0) {
                throw new IOException("Python merge failed with exit code: " + process.exitValue());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Python merge interrupted", e);
        }

        return Files.readAllBytes(outputPath);
    }

    private void splitPdf(byte[] pdfBytes, List<Integer> pageNumbers, Path outputPath) throws IOException {
        if (pageNumbers.isEmpty()) return;

        try (PDDocument source = Loader.loadPDF(pdfBytes);
             PDDocument target = new PDDocument()) {
            for (int pageNum : pageNumbers) {
                if (pageNum >= 1 && pageNum <= source.getNumberOfPages()) {
                    target.addPage(source.getPage(pageNum - 1));
                }
            }
            target.save(outputPath.toFile());
        }
    }

    private byte[] convertWithLibreOffice(byte[] pdfBytes) throws IOException {
        String libreOfficePath = findLibreOffice();
        if (libreOfficePath == null) return convertWithPdf2Docx(pdfBytes);

        Path tempDir = Files.createTempDirectory("pdf-convert-lo-");
        Path pdfPath = tempDir.resolve("input.pdf");

        try {
            Files.write(pdfPath, pdfBytes);
            ProcessBuilder pb = new ProcessBuilder(
                    libreOfficePath,
                    "--headless",
                    "--infilter=writer_pdf_import",
                    "--convert-to", "docx:MS Word 2007 XML",
                    "--outdir", tempDir.toString(),
                    pdfPath.toString());

            pb.redirectErrorStream(true);
            Process process = pb.start();
            readProcessOutput(process);

            boolean completed = process.waitFor(120, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("LibreOffice timeout");
            }

            Path docxPath = tempDir.resolve("input.docx");
            if (!Files.exists(docxPath)) return convertWithPdf2Docx(pdfBytes);

            return Files.readAllBytes(docxPath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Conversion interrupted", e);
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private byte[] convertWithPdf2Docx(byte[] pdfBytes) throws IOException {
        String pythonPath = findPython();
        if (pythonPath == null) throw new IOException("Python không được cài đặt");

        Path scriptPath = getScriptPath();
        if (!Files.exists(scriptPath)) throw new IOException("Script convert_pdf.py không tồn tại");

        Path tempDir = Files.createTempDirectory("pdf-convert-py-");
        Path pdfPath = tempDir.resolve("input.pdf");
        Path docxPath = tempDir.resolve("output.docx");

        try {
            Files.write(pdfPath, pdfBytes);
            ProcessBuilder pb = new ProcessBuilder(
                    pythonPath, scriptPath.toString(), pdfPath.toString(), docxPath.toString());

            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = readProcessOutput(process);

            boolean completed = process.waitFor(120, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("pdf2docx timeout");
            }

            if (!Files.exists(docxPath)) throw new IOException("pdf2docx error: " + output);

            return Files.readAllBytes(docxPath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Conversion interrupted", e);
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private Path getScriptPath() {
        String[] possiblePaths = {
                "scripts/convert_pdf.py",
                "src/main/resources/scripts/convert_pdf.py",
                "../scripts/convert_pdf.py"
        };
        for (String path : possiblePaths) {
            Path p = Paths.get(path).toAbsolutePath();
            if (Files.exists(p)) return p;
        }
        return Paths.get("scripts/convert_pdf.py").toAbsolutePath();
    }

    private String findPython() {
        String[] commands = { "python", "python3", "py" };
        for (String cmd : commands) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                Process p = pb.start();
                if (p.waitFor() == 0) return cmd;
            } catch (Exception ignored) {
            }
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            String[] versions = { "312", "311", "310", "39" };
            for (String v : versions) {
                String path = localAppData + "\\Programs\\Python\\Python" + v + "\\python.exe";
                if (new File(path).exists()) return path;
            }
        }
        return null;
    }

    private String findLibreOffice() {
        for (String path : LIBREOFFICE_PATHS) {
            if (new File(path).exists()) return path;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("soffice", "--version");
            pb.redirectErrorStream(true);
            if (pb.start().waitFor() == 0) return "soffice";
        } catch (Exception ignored) {
        }
        return null;
    }

    private String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
        }
        return output.toString();
    }

    private void deleteDirectory(Path path) {
        try {
            Files.walk(path)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {
        }
    }
}
