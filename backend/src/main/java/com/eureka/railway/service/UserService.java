package com.eureka.railway.service;

import com.eureka.railway.entity.User;
import com.eureka.railway.repository.UserRepository;
import com.eureka.railway.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UserService {

    // a reset link stays valid for this many minutes
    private static final int RESET_TOKEN_VALID_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final NotificationService notificationService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       NotificationService notificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.notificationService = notificationService;
    }

    public User register(String name, String email, String password) {
        return createUser(name, email, password, "USER");
    }

    // only called from the admin-only endpoint, so a new admin can be created
    public User createAdmin(String name, String email, String password) {
        return createUser(name, email, password, "ADMIN");
    }

    private User createUser(String name, String email, String password, String role) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        if (userRepository.existsByEmail(email.trim())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        User user = new User(name.trim(), email.trim(), passwordEncoder.encode(password), role);
        return userRepository.save(user);
    }

    // returns a JWT token if email + password are correct
    public String login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        return jwtUtil.generateToken(user.getEmail(), user.getRole());
    }

    // used with the email taken from the JWT; if the account no longer exists
    // (e.g. database was reset) the old token is useless - ask for a re-login
    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Your session is no longer valid. Please logout and login again."));
    }

    // update editable profile fields (email and role are not changeable here)
    public User updateProfile(String email, String name, String phone) {
        User user = getByEmail(email);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        user.setName(name.trim());
        user.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        return userRepository.save(user);
    }

    // Step 1 of "forgot password": create a one-time token and email the reset link.
    // Nothing is revealed to the caller about whether the email exists.
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email.trim()).ifPresent(user -> {
            user.setResetToken(UUID.randomUUID().toString());
            user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(RESET_TOKEN_VALID_MINUTES));
            userRepository.save(user);
            notificationService.sendPasswordReset(user, user.getResetToken(), RESET_TOKEN_VALID_MINUTES);
        });
    }

    // Step 2: validate the token and set the new password
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Reset link is invalid");
        }
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new IllegalArgumentException(
                        "This reset link is invalid or has already been used"));
        if (user.getResetTokenExpiry() == null || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("This reset link has expired. Please request a new one.");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        // the token is single use - clear it so the link cannot be reused
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }

    // change password after verifying the current one
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = getByEmail(email);
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
