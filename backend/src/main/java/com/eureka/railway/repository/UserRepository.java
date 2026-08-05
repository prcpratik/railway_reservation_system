package com.eureka.railway.repository;

import com.eureka.railway.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // used to look up the account during a password reset
    Optional<User> findByResetToken(String resetToken);
}
