package com.kun.aiinterview.common.validation;

import com.kun.aiinterview.common.exception.BusinessException;

/** Password input checks also apply to service calls without a validation proxy. */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_UTF8_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        validateCurrent(password);
        if (password.length() < MIN_LENGTH) {
            throw new BusinessException("密码长度不能少于8位");
        }
    }

    public static void validateCurrent(String password) {
        // Match the existing Hibernate Validator 8 @NotBlank contract without rewriting input.
        if (password == null || password.trim().isEmpty()) {
            throw new BusinessException("密码不能为空");
        }
        if (!Utf8ByteSizeValidator.isWithinLimit(password, MAX_UTF8_BYTES)) {
            throw new BusinessException("密码UTF-8编码不能超过72字节");
        }
    }
}
