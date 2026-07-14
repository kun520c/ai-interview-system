package com.kun.aiinterview.auth.controller;

import com.kun.aiinterview.auth.dto.RegisterRequest;
import com.kun.aiinterview.auth.service.AuthService;
import com.kun.aiinterview.common.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody  RegisterRequest registerRequest){
        authService.register(registerRequest);
        return Result.success();
    }
}
