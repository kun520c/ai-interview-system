package com.kun.aiinterview.common.validation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Utf8ByteSizeValidatorTest {

    @Test
    void shouldAcceptAndRejectAsciiAtMysqlTextByteBoundary() {
        String maximum = "a".repeat(65_535);
        String oversized = "a".repeat(65_536);

        assertThat(maximum.getBytes(StandardCharsets.UTF_8)).hasSize(65_535);
        assertThat(oversized.getBytes(StandardCharsets.UTF_8)).hasSize(65_536);
        assertThat(Utf8ByteSizeValidator.isWithinLimit(
                maximum,
                Utf8ByteSize.MYSQL_TEXT_MAX_BYTES
        )).isTrue();
        assertThat(Utf8ByteSizeValidator.isWithinLimit(
                oversized,
                Utf8ByteSize.MYSQL_TEXT_MAX_BYTES
        )).isFalse();
    }

    @Test
    void shouldUseUtf8BytesForSupplementaryUnicodeBoundary() {
        String maximum = "😀".repeat(16_383);
        String oversized = "😀".repeat(16_384);

        assertThat(maximum.length()).isEqualTo(32_766);
        assertThat(maximum.getBytes(StandardCharsets.UTF_8)).hasSize(65_532);
        assertThat(oversized.getBytes(StandardCharsets.UTF_8)).hasSize(65_536);
        assertThat(Utf8ByteSizeValidator.isWithinLimit(
                maximum,
                Utf8ByteSize.MYSQL_TEXT_MAX_BYTES
        )).isTrue();
        assertThat(Utf8ByteSizeValidator.isWithinLimit(
                oversized,
                Utf8ByteSize.MYSQL_TEXT_MAX_BYTES
        )).isFalse();
    }
}
