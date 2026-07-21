package com.kun.aiinterview.user.controller;

import com.kun.aiinterview.common.response.Result;
import com.kun.aiinterview.security.model.AuthenticatedUser;
import com.kun.aiinterview.user.dto.ChangePasswordRequest;
import com.kun.aiinterview.user.service.UserService;
import com.kun.aiinterview.user.vo.CurrentUserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public Result<CurrentUserResponse> getCurrentUser(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        Long userId = authenticatedUser.userId();
        CurrentUserResponse currentUserResponse = userService.getCurrentUser(userId);
        return Result.success(currentUserResponse);
    }

    @PutMapping("/me/password")
    public Result<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody ChangePasswordRequest changePasswordRequest
            ){
        userService.changePassword(
                authenticatedUser.userId(),
                changePasswordRequest
        );

        return Result.success();
    }
}
