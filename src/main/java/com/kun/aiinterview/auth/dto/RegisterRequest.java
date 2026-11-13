package com.kun.aiinterview.auth.dto;

import com.kun.aiinterview.common.validation.PasswordPolicy;
import com.kun.aiinterview.common.validation.Utf8ByteSize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString(exclude = "password")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RegisterRequest {
    @NotBlank(message = "账号不能为空")
    @Size(max = 50, message = "账号长度不能超过50位")
    private String account;
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50位")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Size(min = PasswordPolicy.MIN_LENGTH, message = "密码长度不能少于8位")
    @Utf8ByteSize(max = PasswordPolicy.MAX_UTF8_BYTES, message = "密码UTF-8编码不能超过72字节")
    private String password;
    @NotBlank(message = "邮箱不能为空")
    @Size(max = 100, message = "邮箱长度不能超过100位")
    @Email(message = "邮箱格式不正确")
    private String email;
}
