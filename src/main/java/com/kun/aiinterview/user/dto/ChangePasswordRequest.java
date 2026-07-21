package com.kun.aiinterview.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString(exclude = {"currentPassword","newPassword"})
public class ChangePasswordRequest {
    @NotBlank(message = "密码不能为空")
    @Size(max = 72,message = "当前密码不能超过72位")
    private String currentPassword;
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8,max = 72,message = "新密码长度应为8到72位")
    private String newPassword;
}
