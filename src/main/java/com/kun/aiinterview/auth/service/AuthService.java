package com.kun.aiinterview.auth.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.auth.dto.RegisterRequest;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Transactional
@Validated
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public void register(@Valid RegisterRequest registerRequest) {
     if(userMapper.getUserByAccount(registerRequest.getAccount())!=null){
         throw new BusinessException("账号已存在");
        }

     if(userMapper.getUserByEmail(registerRequest.getEmail())!=null){
         throw new BusinessException("邮箱已被注册");
     }

     String encodedPassword = passwordEncoder.encode(registerRequest.getPassword());

     User user  = User.builder()
             .account(registerRequest.getAccount())
             .username(registerRequest.getUsername())
             .password(encodedPassword)
             .email(registerRequest.getEmail())
             .role(UserRole.USER)
             .status(UserStatus.ENABLED)
             .build();

     userMapper.insertUser(user);
    }
}
