package com.flowernailbar.service;

import com.flowernailbar.model.User;
import com.flowernailbar.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * AuthService — handles signup and login.
 *
 * Storage:
 *   Passwords are stored as BCrypt hashes in the users.password_hash column.
 *   We use spring-security-crypto's BCryptPasswordEncoder (no full Spring
 *   Security filter chain — we manage sessions ourselves via HttpSession in
 *   the controller).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserRepository userRepository;

    private BCryptPasswordEncoder encoder;

    @PostConstruct
    public void init() {
        this.encoder = new BCryptPasswordEncoder();
    }

    /**
     * Sign up a new user with a password.
     *
     * If an account with this email already exists AND has a password,
     * we throw — they should log in instead.
     *
     * If an account exists WITHOUT a password (legacy guest from a prior
     * booking), we attach a password to the existing row instead of
     * creating a duplicate.
     *
     * @return the saved User (with id populated)
     */
    public User signup(String fullName, String email, String phone, String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }

        String hash = encoder.encode(rawPassword);
        Optional<User> existing = userRepository.findByEmail(email);

        if (existing.isPresent()) {
            User u = existing.get();
            if (u.getPasswordHash() != null && !u.getPasswordHash().isEmpty()) {
                throw new IllegalStateException(
                    "An account with this email already exists. Please sign in instead."
                );
            }
            // Legacy guest row → upgrade it with a password and refresh contact info.
            userRepository.updateContactInfo(u.getId(), fullName, phone);
            userRepository.updatePasswordHash(u.getId(), hash);
            u.setFullName(fullName);
            u.setPhone(phone);
            u.setPasswordHash(hash);
            log.info("[AuthService] Upgraded existing guest user id={} with password.", u.getId());
            return u;
        }

        User u = new User();
        u.setFullName(fullName);
        u.setEmail(email);
        u.setPhone(phone);
        u.setPasswordHash(hash);
        Long id = userRepository.save(u);
        u.setId(id);
        log.info("[AuthService] New signup id={} email={}", id, email);
        return u;
    }

    /**
     * Verify email + password. Returns the User on success, empty otherwise.
     * Caller is responsible for putting the User in HttpSession.
     */
    public Optional<User> login(String email, String rawPassword) {
        Optional<User> opt = userRepository.findByEmail(email);
        if (opt.isEmpty()) {
            log.info("[AuthService] Login failed: no user for email={}", email);
            return Optional.empty();
        }
        User u = opt.get();
        if (u.getPasswordHash() == null || u.getPasswordHash().isEmpty()) {
            log.info("[AuthService] Login failed: user id={} has no password set (legacy guest)", u.getId());
            return Optional.empty();
        }
        if (!encoder.matches(rawPassword, u.getPasswordHash())) {
            log.info("[AuthService] Login failed: bad password for email={}", email);
            return Optional.empty();
        }
        log.info("[AuthService] Login success: user id={} email={}", u.getId(), email);
        return Optional.of(u);
    }
}
