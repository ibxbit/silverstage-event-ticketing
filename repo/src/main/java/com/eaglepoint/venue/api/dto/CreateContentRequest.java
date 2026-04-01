package com.eaglepoint.venue.api.dto;

import javax.validation.constraints.NotBlank;

public class CreateContentRequest {
    @NotBlank
    private String title;

    @NotBlank
    private String body;

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
}
