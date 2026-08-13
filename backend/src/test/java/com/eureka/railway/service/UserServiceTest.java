package com.eureka.railway.service;

import com.eureka.railway.entity.User;
import com.eureka.railway.repository.UserRepository;
import com.eureka.railway.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// unit tests for the forgot/reset password flow
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("Test User", "test@mail.com", "oldEncoded", "USER");
    }

    @Test
    void requestPasswordResetStoresTokenAndSendsEmail() {
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.requestPasswordReset("test@mail.com");

        assertNotNull(user.getResetToken());
        assertTrue(user.getResetTokenExpiry().isAfter(LocalDateTime.now()));
        verify(notificationService).sendPasswordReset(eq(user), eq(user.getResetToken()), anyInt());
    }

    @Test
    void requestPasswordResetForUnknownEmailIsSilentlyIgnored() {
        when(userRepository.findByEmail("nobody@mail.com")).thenReturn(Optional.empty());

        // must not throw - otherwise the response would reveal that the email is not registered
        assertDoesNotThrow(() -> userService.requestPasswordReset("nobody@mail.com"));
        verify(notificationService, never()).sendPasswordReset(any(), any(), anyInt());
    }

    @Test
    void resetPasswordWithValidTokenUpdatesPasswordAndClearsToken() {
        user.setResetToken("valid-token");
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByResetToken("valid-token")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass123")).thenReturn("newEncoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.resetPassword("valid-token", "newpass123");

        assertEquals("newEncoded", user.getPassword());
        assertNull(user.getResetToken(), "token must be cleared so the link cannot be reused");
        assertNull(user.getResetTokenExpiry());
    }

    @Test
    void resetPasswordFailsWithExpiredToken() {
        user.setResetToken("old-token");
        user.setResetTokenExpiry(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByResetToken("old-token")).thenReturn(Optional.of(user));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> userService.resetPassword("old-token", "newpass123"));
        assertTrue(e.getMessage().contains("expired"));
    }

    @Test
    void resetPasswordFailsWithUnknownToken() {
        when(userRepository.findByResetToken("bad-token")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> userService.resetPassword("bad-token", "newpass123"));
    }

    @Test
    void resetPasswordRejectsShortPassword() {
        user.setResetToken("valid-token");
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByResetToken("valid-token")).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class,
                () -> userService.resetPassword("valid-token", "123"));
    }
}
