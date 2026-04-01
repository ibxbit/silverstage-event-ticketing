package com.eaglepoint.venue.api.dto;

import javax.validation.constraints.NotBlank;

public class ModerationDecisionRequest {
    @NotBlank
    private String penaltyType;

    @NotBlank
    private String decisionNotes;

    public String getPenaltyType() {
        return penaltyType;
    }

    public void setPenaltyType(String penaltyType) {
        this.penaltyType = penaltyType;
    }

    public String getDecisionNotes() {
        return decisionNotes;
    }

    public void setDecisionNotes(String decisionNotes) {
        this.decisionNotes = decisionNotes;
    }
}
