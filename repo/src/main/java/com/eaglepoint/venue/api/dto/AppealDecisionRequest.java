package com.eaglepoint.venue.api.dto;

import javax.validation.constraints.NotBlank;

public class AppealDecisionRequest {
    @NotBlank
    private String status;

    @NotBlank
    private String reviewNotes;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }
}
