package com.eaglepoint.venue.api.dto;

import java.util.ArrayList;
import java.util.List;

public class LoginResponse {
    private String token;
    private String username;
    private String role;
    private List<String> visibleMenus = new ArrayList<String>();

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public List<String> getVisibleMenus() { return visibleMenus; }
    public void setVisibleMenus(List<String> visibleMenus) { this.visibleMenus = visibleMenus; }
}
