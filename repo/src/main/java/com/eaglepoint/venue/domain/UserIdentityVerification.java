package com.eaglepoint.venue.domain;

import java.time.LocalDateTime;

public class UserIdentityVerification {
    private Long id;
    private Long userId;
    private String fullNameEncrypted;
    private String idType;
    private String idNumberEncrypted;
    private String idNumberMasked;
    private String status;
    private String reviewedBy;
    private String reviewNotes;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getFullNameEncrypted() { return fullNameEncrypted; }
    public void setFullNameEncrypted(String fullNameEncrypted) { this.fullNameEncrypted = fullNameEncrypted; }
    public String getIdType() { return idType; }
    public void setIdType(String idType) { this.idType = idType; }
    public String getIdNumberEncrypted() { return idNumberEncrypted; }
    public void setIdNumberEncrypted(String idNumberEncrypted) { this.idNumberEncrypted = idNumberEncrypted; }
    public String getIdNumberMasked() { return idNumberMasked; }
    public void setIdNumberMasked(String idNumberMasked) { this.idNumberMasked = idNumberMasked; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
