package com.eaglepoint.venue.api.dto;

import javax.validation.constraints.NotBlank;

public class UpdateContentRequest {
    @NotBlank
    private String title;

    @NotBlank
    private String body;

    @NotBlank
    private String summary;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
