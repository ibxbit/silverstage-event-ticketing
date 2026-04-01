package com.eaglepoint.venue.api.dto;

import javax.validation.constraints.NotBlank;

public class VerificationReviewRequest {
    @NotBlank
    private String status;
    private String notes;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
