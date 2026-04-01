package com.eaglepoint.venue.api.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReportResponse {
    private Long reportId;
    private String status;
    private String reporterUser;
    private String reportedUser;
    private String reason;
    private String penaltyType;
    private LocalDateTime penaltyEndsAt;
    private List<String> evidenceFiles = new ArrayList<String>();

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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

    public List<String> getEvidenceFiles() {
        return evidenceFiles;
    }

    public void setEvidenceFiles(List<String> evidenceFiles) {
        this.evidenceFiles = evidenceFiles;
    }
}
