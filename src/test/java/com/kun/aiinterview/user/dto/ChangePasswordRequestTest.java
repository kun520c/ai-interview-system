package com.kun.aiinterview.user.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ChangePasswordRequestTest {

    @Test
    void shouldExcludePasswordsFromToString() {
        String currentPassword = "CurrentPassword123!";
        String newPassword = "NewPassword456!";
        ChangePasswordRequest request = new ChangePasswordRequest(
                currentPassword,
                newPassword
        );

        String requestText = request.toString();

        assertFalse(requestText.contains(currentPassword));
        assertFalse(requestText.contains(newPassword));
    }
}
