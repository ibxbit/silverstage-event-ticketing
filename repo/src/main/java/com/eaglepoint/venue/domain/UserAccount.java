package com.eaglepoint.venue.domain;

import java.time.LocalDateTime;

public class UserAccount {
    private Long id;
    private String username;
    private String passwordHash;
    private String role;
    private Integer failedAttempts;
    private LocalDateTime lockoutUntil;
    private String active;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Integer getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(Integer failedAttempts) { this.failedAttempts = failedAttempts; }
    public LocalDateTime getLockoutUntil() { return lockoutUntil; }
    public void setLockoutUntil(LocalDateTime lockoutUntil) { this.lockoutUntil = lockoutUntil; }
    public String getActive() { return active; }
    public void setActive(String active) { this.active = active; }
}
