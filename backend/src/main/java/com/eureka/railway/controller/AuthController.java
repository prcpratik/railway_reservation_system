package com.eureka.railway.controller;

import com.eureka.railway.dto.Dtos.AuthResponse;
import com.eureka.railway.dto.Dtos.ChangePasswordRequest;
import com.eureka.railway.dto.Dtos.ForgotPasswordRequest;
import com.eureka.railway.dto.Dtos.LoginRequest;
import com.eureka.railway.dto.Dtos.ResetPasswordRequest;
import com.eureka.railway.dto.Dtos.ProfileResponse;
import com.eureka.railway.dto.Dtos.RegisterRequest;
import com.eureka.railway.dto.Dtos.UpdateProfileRequest;
import com.eureka.railway.entity.User;
import com.eureka.railway.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody RegisterRequest request) {
        User user = userService.register(request.name(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        String token = userService.login(request.email(), request.password());
        User user = userService.getByEmail(request.email());
        return new AuthResponse(token, user.getName(), user.getEmail(), user.getRole());
    }

    // step 1 of forgot password - always the same reply, so nobody can use this
    // endpoint to find out which email addresses are registered
    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        userService.requestPasswordReset(request.email());
        return Map.of("message",
                "If that email is registered, a password reset link has been sent to it.");
    }

    // step 2 - the link from the email lands here with its token
    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request.token(), request.newPassword());
        return Map.of("message", "Password reset successfully. You can now log in.");
    }

    // current logged-in user's profile (email comes from the JWT)
    @GetMapping("/me")
    public ProfileResponse me(Authentication authentication) {
        return ProfileResponse.from(userService.getByEmail(authentication.getName()));
    }

    @PutMapping("/profile")
    public ProfileResponse updateProfile(@RequestBody UpdateProfileRequest request, Authentication authentication) {
        User updated = userService.updateProfile(authentication.getName(), request.name(), request.phone());
        return ProfileResponse.from(updated);
    }

    @PutMapping("/password")
    public Map<String, String> changePassword(@RequestBody ChangePasswordRequest request,
                                              Authentication authentication) {
        userService.changePassword(authentication.getName(), request.currentPassword(), request.newPassword());
        return Map.of("message", "Password changed successfully");
    }
}
