package com.kun.aiinterview.user.dto;

import com.kun.aiinterview.common.validation.PasswordPolicy;
import com.kun.aiinterview.common.validation.Utf8ByteSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString(exclude = {"currentPassword","newPassword"})
public class ChangePasswordRequest {
    @NotBlank(message = "密码不能为空")
    @Utf8ByteSize(max = PasswordPolicy.MAX_UTF8_BYTES, message = "当前密码UTF-8编码不能超过72字节")
    private String currentPassword;
    @NotBlank(message = "新密码不能为空")
    @Size(min = PasswordPolicy.MIN_LENGTH, message = "新密码长度不能少于8位")
    @Utf8ByteSize(max = PasswordPolicy.MAX_UTF8_BYTES, message = "新密码UTF-8编码不能超过72字节")
    private String newPassword;
}
