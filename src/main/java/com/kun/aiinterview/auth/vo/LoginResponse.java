package com.kun.aiinterview.auth.vo;

import com.kun.aiinterview.user.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

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
