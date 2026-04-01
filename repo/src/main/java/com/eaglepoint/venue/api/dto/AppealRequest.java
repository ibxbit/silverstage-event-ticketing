package com.eaglepoint.venue.api.dto;

import javax.validation.constraints.NotBlank;

public class AppealRequest {
    @NotBlank
    private String justification;

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }
}
