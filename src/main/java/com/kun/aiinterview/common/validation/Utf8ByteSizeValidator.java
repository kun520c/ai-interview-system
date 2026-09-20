package com.kun.aiinterview.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class Utf8ByteSizeValidator
        implements ConstraintValidator<Utf8ByteSize, String> {

    private int max;

    @Override
    public void initialize(Utf8ByteSize constraintAnnotation) {
        max = constraintAnnotation.max();
        if (max <= 0) {
            throw new IllegalArgumentException(
                    "UTF-8 byte limit must be positive"
            );
        }
    }

    @Override
    public boolean isValid(
            String value,
            ConstraintValidatorContext context
    ) {
        return isWithinLimit(value, max);
    }

    public static boolean isWithinLimit(String value, int maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException(
                    "maxBytes must be positive"
            );
        }

        return value == null
                || value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }
}
