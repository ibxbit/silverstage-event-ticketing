package com.eaglepoint.venue.common;

public enum ContentState {
    DRAFT,
    SUBMISSION,
    REVIEW,
    PUBLISH;

    public String value() {
        return name();
    }
}
