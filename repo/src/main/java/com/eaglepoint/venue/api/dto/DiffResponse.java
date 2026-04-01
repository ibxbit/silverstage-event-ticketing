package com.eaglepoint.venue.api.dto;

import java.util.ArrayList;
import java.util.List;

public class DiffResponse {
    private Long contentId;
    private Integer leftVersion;
    private Integer rightVersion;
    private List<String> leftLines = new ArrayList<String>();
    private List<String> rightLines = new ArrayList<String>();

    public Long getContentId() {
        return contentId;
    }

    public void setContentId(Long contentId) {
        this.contentId = contentId;
    }

    public Integer getLeftVersion() {
        return leftVersion;
    }

    public void setLeftVersion(Integer leftVersion) {
        this.leftVersion = leftVersion;
    }

    public Integer getRightVersion() {
        return rightVersion;
    }

    public void setRightVersion(Integer rightVersion) {
        this.rightVersion = rightVersion;
    }

    public List<String> getLeftLines() {
        return leftLines;
    }

    public void setLeftLines(List<String> leftLines) {
        this.leftLines = leftLines;
    }

    public List<String> getRightLines() {
        return rightLines;
    }

    public void setRightLines(List<String> rightLines) {
        this.rightLines = rightLines;
    }
}
