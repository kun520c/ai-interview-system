package com.kun.aiinterview.auth;

// Enum names keep parameterized test reports free of plaintext passwords.
enum PasswordSample {
    ASCII_MIN("B5test!9"),
    ASCII_72("x".repeat(72)),
    CJK_72("界".repeat(24)),
    EMOJI_72("\uD83D\uDE80".repeat(18)),
    COMBINING_72("e\u0301".repeat(24)),
    SPACES("  B5boundary!  "),
    UNICODE_SPACES("\u3000".repeat(8)),
    INTERIM("B5Interim!2026"),
    ASCII_73("x".repeat(73)),
    MIXED_73("界".repeat(24) + "x"),
    CJK_75("界".repeat(25)),
    EMOJI_76("\uD83D\uDE80".repeat(19)),
    COMBINING_75("e\u0301".repeat(25)),
    EMPTY(""),
    BLANK(" ".repeat(8)),
    CONTROL_BLANK(Character.toString(0).repeat(8)),
    SHORT("B5short"),
    NULL(null);

    final String value;

    PasswordSample(String value) {
        this.value = value;
    }
}
