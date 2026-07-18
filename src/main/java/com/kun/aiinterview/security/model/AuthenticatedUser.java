package com.kun.aiinterview.security.model;

import java.security.Principal;

public record AuthenticatedUser(
        Long userId,
        String account,
        String username
) implements Principal {

    @Override
    public String getName() {
        return account;
    }
}
