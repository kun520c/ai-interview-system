package com.kun.aiinterview.user.entity;

import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@ToString(exclude = "password")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User {

    private Long id;
    private String account;
    private String password;
    private String username;
    private String email;
    private UserRole role;
    private UserStatus status;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
