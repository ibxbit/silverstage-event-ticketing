package com.eaglepoint.venue.domain;

import java.time.LocalDateTime;

public class ContentReport {
    private Long id;
    private String reporterUser;
    private String reportedUser;
    private String contentType;
    private String contentRef;
    private String reason;
    private String status;
    private String moderatorUser;
    private String decisionNotes;
    private String penaltyType;
    private LocalDateTime penaltyEndsAt;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReporterUser() {
        return reporterUser;
    }

    public void setReporterUser(String reporterUser) {
        this.reporterUser = reporterUser;
    }

    public String getReportedUser() {
        return reportedUser;
    }

    public void setReportedUser(String reportedUser) {
        this.reportedUser = reportedUser;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentRef() {
        return contentRef;
    }

    public void setContentRef(String contentRef) {
        this.contentRef = contentRef;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getModeratorUser() {
        return moderatorUser;
    }

    public void setModeratorUser(String moderatorUser) {
        this.moderatorUser = moderatorUser;
    }

    public String getDecisionNotes() {
        return decisionNotes;
    }

    public void setDecisionNotes(String decisionNotes) {
        this.decisionNotes = decisionNotes;
    }

    public String getPenaltyType() {
        return penaltyType;
    }

    public void setPenaltyType(String penaltyType) {
        this.penaltyType = penaltyType;
    }

    public LocalDateTime getPenaltyEndsAt() {
        return penaltyEndsAt;
    }

    public void setPenaltyEndsAt(LocalDateTime penaltyEndsAt) {
        this.penaltyEndsAt = penaltyEndsAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
