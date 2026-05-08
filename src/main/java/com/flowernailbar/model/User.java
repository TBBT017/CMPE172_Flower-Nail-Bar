package com.flowernailbar.model;

public class User {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String passwordHash;   // nullable for legacy guest rows
    private String role;           // 'USER' (default) or 'ADMIN'
    private String createdAt;

    public User() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    /** Convenience helper used by templates and controllers. */
    public boolean isAdmin() { return "ADMIN".equalsIgnoreCase(role); }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
