package com.ghanshyam.empty_stack.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Extracts text from uploaded PDF/TXT files and splits it into overlapping
 * word-based chunks for embedding.
 */
@Service
public class ChunkingService {

    private final int chunkSize;
    private final int chunkOverlap;

    public ChunkingService(@Value("${app.rag.chunk-size:500}") int chunkSize,
                           @Value("${app.rag.chunk-overlap:50}") int chunkOverlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunk-size must be positive");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            // overlap >= size would never advance the window (infinite loop)
            throw new IllegalArgumentException("chunk-overlap must be >= 0 and smaller than chunk-size");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    /** Entry point: give it a file, get back a list of text chunks. */
    public List<String> chunkFile(MultipartFile file) throws IOException {
        return splitIntoChunks(extractText(file));
    }

    /** Extracts raw text from a PDF or plain-text file. */
    String extractText(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String text;
        if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
                text = new PDFTextStripper().getText(doc);
            }
        } else {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        }
        // PostgreSQL TEXT columns reject NUL bytes, which some PDFs contain
        return text.replace("\u0000", "");
    }

    /**
     * Splits text into chunks of {@code chunkSize} words where consecutive chunks
     * share {@code chunkOverlap} words, so sentences on a boundary keep their context.
     */
    List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        String[] words = text.strip().split("\\s+");
        int step = chunkSize - chunkOverlap;

        for (int start = 0; start < words.length; start += step) {
            int end = Math.min(start + chunkSize, words.length);
            chunks.add(String.join(" ", Arrays.copyOfRange(words, start, end)));
            if (end == words.length) {
                break; // last window reached; avoid emitting a redundant tail chunk
            }
        }
        return chunks;
    }
}
