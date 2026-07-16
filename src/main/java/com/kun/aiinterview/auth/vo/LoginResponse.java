package com.kun.aiinterview.auth.vo;

import com.kun.aiinterview.user.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    private Long userId;
    private String account;
    private String username;
    private UserRole role;
}
