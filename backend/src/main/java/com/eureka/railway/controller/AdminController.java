package com.eureka.railway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eureka.railway.dto.Dtos.ProfileResponse;
import com.eureka.railway.dto.Dtos.RegisterRequest;
import com.eureka.railway.entity.User;
import com.eureka.railway.service.UserService;

// admin-only endpoints. Access is restricted to ROLE_ADMIN in SecurityConfig
// (all of /api/admin/** requires an admin token).
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    // create a new admin account - only an existing admin can call this
    @PostMapping("/create-admin")
    public ResponseEntity<ProfileResponse> createAdmin(@RequestBody RegisterRequest request) {
        User admin = userService.createAdmin(request.name(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(admin));
    }
}


