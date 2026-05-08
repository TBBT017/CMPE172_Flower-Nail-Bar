package com.flowernailbar.repository;

import com.flowernailbar.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * UserRepository — raw JDBC access to the users table.
 * No ORM, no auto-generated SQL. All queries written by hand.
 */
@Repository
public class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    @Autowired
    private JdbcTemplate jdbc;

    private final RowMapper<User> userRowMapper = (rs, rowNum) -> {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setFullName(rs.getString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setPhone(rs.getString("phone"));
        u.setPasswordHash(rs.getString("password_hash"));
        // Default to USER if column is null (legacy rows)
        String role = rs.getString("role");
        u.setRole(role == null ? "USER" : role);
        u.setCreatedAt(rs.getString("created_at"));
        return u;
    };

    /**
     * Find a user by email. Returns Optional.empty() if not found.
     */
    public Optional<User> findByEmail(String email) {
        List<User> results = jdbc.query(
            "SELECT * FROM users WHERE email = ?",
            userRowMapper, email
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Find user by ID.
     */
    public Optional<User> findById(Long id) {
        List<User> results = jdbc.query(
            "SELECT * FROM users WHERE id = ?",
            userRowMapper, id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Insert a new user (with optional password_hash + role) and return the generated ID.
     */
    public Long save(User user) {
        final String role = (user.getRole() == null || user.getRole().isBlank()) ? "USER" : user.getRole();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (full_name, email, phone, password_hash, role) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setString(4, user.getPasswordHash()); // may be null for legacy/guest path
            ps.setString(5, role);
            return ps;
        }, keyHolder);

        Long newId = keyHolder.getKey().longValue();
        log.info("[UserRepository] Saved user id={} email={} role={}", newId, user.getEmail(), role);
        return newId;
    }

    /**
     * Set/replace a user's password hash (used when an existing guest-booking
     * user later signs up with a password).
     */
    public void updatePasswordHash(Long userId, String passwordHash) {
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", passwordHash, userId);
        log.info("[UserRepository] Updated password_hash for user id={}", userId);
    }

    /**
     * Update name/phone for an existing user (used when login/signup carries
     * fresher contact info than what's on file).
     */
    public void updateContactInfo(Long userId, String fullName, String phone) {
        jdbc.update("UPDATE users SET full_name = ?, phone = ? WHERE id = ?", fullName, phone, userId);
    }

    /**
     * Find or create a user by email (upsert pattern). No password set.
     */
    public Long findOrCreate(String fullName, String email, String phone) {
        Optional<User> existing = findByEmail(email);
        if (existing.isPresent()) {
            log.info("[UserRepository] Found existing user id={}", existing.get().getId());
            return existing.get().getId();
        }
        User newUser = new User();
        newUser.setFullName(fullName);
        newUser.setEmail(email);
        newUser.setPhone(phone);
        return save(newUser);
    }

    public List<User> findAll() {
        return jdbc.query("SELECT * FROM users ORDER BY created_at DESC", userRowMapper);
    }
}
