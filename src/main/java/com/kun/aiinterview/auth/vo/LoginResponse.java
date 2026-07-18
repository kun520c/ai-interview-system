package com.kun.aiinterview.auth.vo;

import com.kun.aiinterview.user.enums.UserRole;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "accessToken")
public class LoginResponse {
    private Long userId;
    private String account;
    private String username;
    private UserRole role;
    private String accessToken;
    private String tokenType;
    private long expiresInSeconds;
}
