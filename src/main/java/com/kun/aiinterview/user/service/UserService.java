package com.kun.aiinterview.user.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.user.dto.ChangePasswordRequest;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import com.kun.aiinterview.user.vo.CurrentUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

@Service
@Validated
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public CurrentUserResponse getCurrentUser(Long userId) {
        User user = userMapper.getUserById(userId);
        if(user == null) {
            throw new BusinessException("用户不存在");
        }

        if(user.getStatus() == UserStatus.DISABLED){
            throw new BusinessException("账号已被封禁");
        }

        return CurrentUserResponse.builder()
                .userId(userId)
                .account(user.getAccount())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest changePasswordRequest) {
        User user = userMapper.getUserById(userId);
        if(user == null){
            throw new BusinessException("用户不存在");
        }

        if(user.getStatus() == UserStatus.DISABLED){
            throw new BusinessException("账号已被封禁");
        }

        if(!passwordEncoder.matches(changePasswordRequest.getCurrentPassword(),user.getPassword())){
            throw new BusinessException("请输入正确的现存密码");
        }

        if(passwordEncoder.matches(changePasswordRequest.getNewPassword(),user.getPassword())){
            throw new BusinessException("新密码不能与原密码相同");
        }

        String newPassword = passwordEncoder.encode(changePasswordRequest.getNewPassword());
        LocalDateTime passwordChangeTime = LocalDateTime.now().withNano(0);

        int affectedRows = userMapper.updatePassword(userId,newPassword,passwordChangeTime);

        if(affectedRows != 1){
            throw new BusinessException("密码修改失败");
        }
    }
}
