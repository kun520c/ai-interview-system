package com.kun.aiinterview.user.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@ToString(exclude = "password")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RegisterRequest {
    @NotBlank
    @Size(max = 50)
    private String account;
    @NotBlank
    @Size(max = 50)
    private String username;
    @NotBlank
    @Size(min = 8,max = 72)
    private String password;
    @NotBlank
    @Size(max = 100)
    @Email
    private String email;
}
