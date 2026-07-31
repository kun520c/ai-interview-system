package com.kun.aiinterview.knowledge.chunk;

import com.kun.aiinterview.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class KnowledgeTextChunkerTest {

    private static final int MAX_CHUNK_CHARACTERS = 1200;
    private static final int OVERLAP_CHARACTERS = 150;

    private final KnowledgeTextChunker knowledgeTextChunker =
            new KnowledgeTextChunker();

    @ParameterizedTest(name = "[{index}] blank content should be rejected")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t\r\n"})
    void givenNullEmptyOrBlankContent_whenSplitting_thenThrowsClearBusinessException(
            String content
    ) {
        assertThatThrownBy(() -> knowledgeTextChunker.split(content))
                .isInstanceOf(BusinessException.class)
                .hasMessage("待切片文档正文不能为空");
    }

    @Test
    void givenShortDocument_whenSplitting_thenReturnsOneCompleteChunk() {
        String content = "Java 集合框架包含 List、Set 和 Map。";

        List<KnowledgeChunkDraft> chunks = knowledgeTextChunker.split(content);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().chunkIndex()).isEqualTo(1);
        assertThat(chunks.getFirst().content()).isEqualTo(content);
        assertThat(chunks.getFirst().characterCount())
                .isEqualTo(content.length());
        assertChunkMetadata(chunks);
    }

    @Test
    void givenShortDocumentWithOuterWhitespace_whenSplitting_thenStripsChunkEdges() {
        String content = " \n\t Java 集合框架 \r\n ";

        List<KnowledgeChunkDraft> chunks = knowledgeTextChunker.split(content);

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.content()).isEqualTo("Java 集合框架");
            assertThat(chunk.characterCount())
                    .isEqualTo(chunk.content().length());
        });
    }

    @Test
    void givenDocumentExactlyAtMaximum_whenSplitting_thenReturnsOneMaximumChunk() {
        String content = "a".repeat(MAX_CHUNK_CHARACTERS);

        List<KnowledgeChunkDraft> chunks = knowledgeTextChunker.split(content);

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.content()).isEqualTo(content);
            assertThat(chunk.characterCount())
                    .isEqualTo(MAX_CHUNK_CHARACTERS);
        });
    }

    @Test
    void givenDocumentOneCharacterOverMaximum_whenSplitting_thenKeepsRemainder() {
        String content = "a".repeat(MAX_CHUNK_CHARACTERS) + "Z";

        List<KnowledgeChunkDraft> chunks = knowledgeTextChunker.split(content);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.getFirst().content())
                .hasSize(MAX_CHUNK_CHARACTERS);
        assertThat(chunks.getLast().content()).endsWith("Z");
        assertThat(sharedSuffixPrefixLength(
                chunks.getFirst().content(),
                chunks.getLast().content()
        )).isPositive();
        assertChunkMetadata(chunks);
    }

    @Test
    void givenLongDocumentWithoutNaturalBoundaries_whenSplitting_thenHardCutsSafely() {
        String content = buildBoundaryFreeText(4200);

        List<KnowledgeChunkDraft> chunks = knowledgeTextChunker.split(content);

        assertThat(chunks).hasSizeGreaterThan(3);
        assertThat(chunks)
                .allSatisfy(chunk -> assertThat(chunk.content().length())
                        .isLessThanOrEqualTo(MAX_CHUNK_CHARACTERS));
        assertThat(chunks.getFirst().content())
                .isEqualTo(content.substring(0, MAX_CHUNK_CHARACTERS));
        assertThat(chunks.getLast().content())
                .isEqualTo(content.substring(
                        content.length() - chunks.getLast().content().length()
                ));
        assertChunkMetadata(chunks);
    }

    @Test
    void givenParagraphNewlineAndSentenceBoundaries_whenSplitting_thenPrefersParagraph() {
        String expectedFirstChunk = "a".repeat(820)
                + "."
                + "b".repeat(79);
        String content = expectedFirstChunk
                + "\n\n"
                + "c".repeat(198)
                + "\n"
                + "d".repeat(99)
                + ";"
                + "e".repeat(200);

        List<KnowledgeChunkDraft> chunks = knowledgeTextChunker.split(content);

        assertThat(chunks.getFirst().content()).isEqualTo(expectedFirstChunk);
        assertChunkMetadata(chunks);
    }

    @Test
    void givenNewlineAndSentenceBoundaries_whenSplitting_thenPrefersNewline() {
        String expectedFirstChunk = "a".repeat(820)
                + "."
                + "b".repeat(179);
        String content = expectedFirstChunk
                + "\n"
                + "c".repeat(149)
                + ";"
                + "d".repeat(200);

        List<KnowledgeChunkDraft> chunks = knowledgeTextChunker.split(content);

        assertThat(chunks.getFirst().content()).isEqualTo(expectedFirstChunk);
        assertChunkMetadata(chunks);
    }

    @ParameterizedTest(name = "sentence terminator {0} should be retained")
    @ValueSource(chars = {'。', '！', '？', '；', '.', '!', '?', ';'})
    void givenSupportedSentenceBoundary_whenSplitting_thenRetainsItInPreviousChunk(
            char sentenceBoundary
    ) {
        String expectedFirstChunk = "a".repeat(900) + sentenceBoundary;
        String content = expectedFirstChunk + "b".repeat(400);

        List<KnowledgeChunkDraft> chunks = knowledgeTextChunker.split(content);

        assertThat(chunks.getFirst().content()).isEqualTo(expectedFirstChunk);
        assertChunkMetadata(chunks);
    }

    @Test
    void givenLongDocument_whenSplitting_thenAdjacentChunksShareProperContext() {
        String content = buildEnglishWords(500);

        List<KnowledgeChunkDraft> chunks = knowledgeTextChunker.split(content);

        assertThat(chunks).hasSizeGreaterThan(2);
        for (int index = 1; index < chunks.size(); index++) {
            String previous = chunks.get(index - 1).content();
            String current = chunks.get(index).content();
            int sharedLength = sharedSuffixPrefixLength(previous, current);

            assertThat(sharedLength).isPositive();
            assertThat(sharedLength).isLessThan(previous.length());
            assertThat(current).isNotEqualTo(previous);
        }

        for (int wordIndex = 0; wordIndex < 500; wordIndex++) {
            String word = "word%04d".formatted(wordIndex);
            assertThat(chunks)
                    .anySatisfy(chunk -> assertThat(chunk.content())
                            .contains(word));
        }
        assertChunkMetadata(chunks);
    }

    @Test
    void givenLongEnglishDocument_whenSplitting_thenLaterChunksStartAtWholeWords() {
        String content = buildEnglishWords(500);

        List<KnowledgeChunkDraft> chunks = knowledgeTextChunker.split(content);

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks.stream().skip(1))
                .allSatisfy(chunk -> {
                    String firstWord = chunk.content().split("\\s+", 2)[0];
                    assertThat(firstWord).matches("word\\d{4}");
                });
    }

    @Test
    void givenOverlongWordWithoutWhitespace_whenSplitting_thenStillTerminates() {
        String content = "x".repeat(5000);

        List<KnowledgeChunkDraft> chunks = assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> knowledgeTextChunker.split(content)
        );

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content())
                .isNotBlank());
        assertChunkMetadata(chunks);
    }

    @Test
    void givenLongChineseDocumentWithoutSpaces_whenSplitting_thenAdvancesStably() {
        String content = "这是没有空格的中文内容".repeat(500);

        List<KnowledgeChunkDraft> chunks = assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> knowledgeTextChunker.split(content)
        );

        assertThat(chunks).hasSizeGreaterThan(1);
        assertChunkMetadata(chunks);
    }

    @Test
    void givenSameInputTwice_whenSplitting_thenResultsAreDeterministicAndIndependent() {
        String content = buildEnglishWords(450);

        List<KnowledgeChunkDraft> first = knowledgeTextChunker.split(content);
        List<KnowledgeChunkDraft> second = knowledgeTextChunker.split(content);

        assertThat(first).hasSameSizeAs(second);
        for (int index = 0; index < first.size(); index++) {
            assertThat(first.get(index).chunkIndex())
                    .isEqualTo(second.get(index).chunkIndex());
            assertThat(first.get(index).content())
                    .isEqualTo(second.get(index).content());
            assertThat(first.get(index).characterCount())
                    .isEqualTo(second.get(index).characterCount());
            assertThat(first.get(index)).isNotSameAs(second.get(index));
        }

        int secondSize = second.size();
        first.clear();
        assertThat(second).hasSize(secondSize);
    }

    @Test
    void givenLongLeadingWhitespace_whenSplitting_thenDoesNotReturnEmptyChunks() {
        String content = " ".repeat(1300) + "content";

        List<KnowledgeChunkDraft> chunks = knowledgeTextChunker.split(content);

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.chunkIndex()).isEqualTo(1);
            assertThat(chunk.content()).isEqualTo("content");
            assertThat(chunk.characterCount()).isEqualTo(7);
        });
    }

    @Test
    void givenParagraphDelimiterCrossingHardEnd_whenSplitting_thenDoesNotExtendWindow() {
        String content = "a".repeat(MAX_CHUNK_CHARACTERS - 1)
                + "\n\n"
                + "b".repeat(200);

        List<KnowledgeChunkDraft> chunks = knowledgeTextChunker.split(content);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(1).content())
                .startsWith("a".repeat(OVERLAP_CHARACTERS - 1) + "\n\nb");
        assertChunkMetadata(chunks);
    }

    @Test
    void givenConcurrentCalls_whenSplitting_thenSingletonComponentHasNoSharedState() {
        String content = buildEnglishWords(450);
        List<KnowledgeChunkDraft> expected = knowledgeTextChunker.split(content);

        List<List<KnowledgeChunkDraft>> concurrentResults = IntStream.range(0, 12)
                .parallel()
                .mapToObj(ignored -> knowledgeTextChunker.split(content))
                .toList();

        assertThat(concurrentResults).allSatisfy(actual -> {
            assertThat(actual).hasSameSizeAs(expected);
            for (int index = 0; index < expected.size(); index++) {
                assertThat(actual.get(index).chunkIndex())
                        .isEqualTo(expected.get(index).chunkIndex());
                assertThat(actual.get(index).content())
                        .isEqualTo(expected.get(index).content());
                assertThat(actual.get(index).characterCount())
                        .isEqualTo(expected.get(index).characterCount());
            }
        });
    }

    private static void assertChunkMetadata(List<KnowledgeChunkDraft> chunks) {
        assertThat(chunks).isNotEmpty();
        assertThat(chunks)
                .extracting(KnowledgeChunkDraft::chunkIndex)
                .containsExactlyElementsOf(
                        IntStream.rangeClosed(1, chunks.size()).boxed().toList()
                );
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.content()).isNotBlank();
            assertThat(chunk.characterCount())
                    .isEqualTo(chunk.content().length());
        });
    }

    private static int sharedSuffixPrefixLength(String previous, String current) {
        int maximum = Math.min(previous.length(), current.length());
        for (int length = maximum; length > 0; length--) {
            if (previous.regionMatches(
                    previous.length() - length,
                    current,
                    0,
                    length
            )) {
                return length;
            }
        }
        return 0;
    }

    private static String buildEnglishWords(int wordCount) {
        return IntStream.range(0, wordCount)
                .mapToObj(index -> "word%04d".formatted(index))
                .reduce((left, right) -> left + " " + right)
                .orElseThrow();
    }

    private static String buildBoundaryFreeText(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append((char) (0x4E00 + index % 2000));
        }
        return builder.toString();
    }
}
