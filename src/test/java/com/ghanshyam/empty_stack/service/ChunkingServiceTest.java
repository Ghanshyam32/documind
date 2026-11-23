package com.ghanshyam.empty_stack.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkingServiceTest {

    private static String words(int from, int toExclusive) {
        return IntStream.range(from, toExclusive).mapToObj(i -> "w" + i).collect(Collectors.joining(" "));
    }

    @Test
    void shortTextProducesSingleChunk() {
        ChunkingService service = new ChunkingService(10, 2);

        assertThat(service.splitIntoChunks("hello   world\n\tagain"))
                .containsExactly("hello world again");
    }

    @Test
    void consecutiveChunksShareOverlapWords() {
        ChunkingService service = new ChunkingService(10, 3);

        List<String> chunks = service.splitIntoChunks(words(0, 24));

        assertThat(chunks).containsExactly(
                words(0, 10),
                words(7, 17),
                words(14, 24));
    }

    @Test
    void doesNotEmitRedundantTailChunk() {
        ChunkingService service = new ChunkingService(10, 2);

        // exactly one window of text must be one chunk, not a full chunk plus a 2-word tail
        assertThat(service.splitIntoChunks(words(0, 10))).containsExactly(words(0, 10));
    }

    @Test
    void everyWordIsCovered() {
        ChunkingService service = new ChunkingService(50, 10);

        List<String> chunks = service.splitIntoChunks(words(0, 1234));

        String joined = String.join(" ", chunks) + " ";
        IntStream.range(0, 1234).forEach(i -> assertThat(joined).contains("w" + i + " "));
        assertThat(chunks).allSatisfy(c -> assertThat(c.split(" ")).hasSizeLessThanOrEqualTo(50));
    }

    @Test
    void blankTextProducesNoChunks() {
        ChunkingService service = new ChunkingService(10, 2);

        assertThat(service.splitIntoChunks("")).isEmpty();
        assertThat(service.splitIntoChunks("   \n\t ")).isEmpty();
        assertThat(service.splitIntoChunks(null)).isEmpty();
    }

    @Test
    void rejectsOverlapThatWouldNeverAdvance() {
        assertThatThrownBy(() -> new ChunkingService(10, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChunkingService(10, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChunkingService(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsUtf8TextFilesAndStripsNulBytes() throws Exception {
        ChunkingService service = new ChunkingService(500, 50);
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain",
                "Leave policy: 12 days café\u0000 leave".getBytes(StandardCharsets.UTF_8));

        assertThat(service.chunkFile(file)).containsExactly("Leave policy: 12 days café leave");
    }

    @Test
    void extractsTextFromPdf() throws Exception {
        ChunkingService service = new ChunkingService(500, 50);
        MockMultipartFile file = new MockMultipartFile("file", "Handbook.PDF", "application/pdf",
                pdfWithText("New employees get 12 days of casual leave per year."));

        assertThat(service.chunkFile(file))
                .containsExactly("New employees get 12 days of casual leave per year.");
    }

    private static byte[] pdfWithText(String text) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText(text);
                content.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
