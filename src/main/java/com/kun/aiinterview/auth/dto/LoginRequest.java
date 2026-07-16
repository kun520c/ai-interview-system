package com.kun.aiinterview.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString(exclude = "password")
public class LoginRequest {
    @NotBlank(message = "账号不能为空")
    @Size(max = 50,message = "账号长度不能超过50位")
    private String account;
    @NotBlank(message = "密码不能为空")
    @Size(min = 8,max = 72,message = "密码长度应为8到72位")
    private String password;
}
