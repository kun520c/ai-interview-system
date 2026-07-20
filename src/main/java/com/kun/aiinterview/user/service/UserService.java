package com.kun.aiinterview.user.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import com.kun.aiinterview.user.vo.CurrentUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;

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
}
