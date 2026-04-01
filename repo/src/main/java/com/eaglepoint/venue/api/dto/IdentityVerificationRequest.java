package com.eaglepoint.venue.api.dto;

import javax.validation.constraints.NotBlank;

public class IdentityVerificationRequest {
    @NotBlank
    private String fullName;
    @NotBlank
    private String idType;
    @NotBlank
    private String idNumber;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getIdType() { return idType; }
    public void setIdType(String idType) { this.idType = idType; }
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
}
