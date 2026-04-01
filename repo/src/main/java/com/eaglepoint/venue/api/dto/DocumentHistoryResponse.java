package com.eaglepoint.venue.api.dto;

import java.util.ArrayList;
import java.util.List;

public class DocumentHistoryResponse {
    private Long documentId;
    private String title;
    private String folderPath;
    private String accessLevel;
    private List<String> tags = new ArrayList<String>();
    private List<DocumentVersionItem> versions = new ArrayList<DocumentVersionItem>();

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public String getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(String accessLevel) {
        this.accessLevel = accessLevel;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<DocumentVersionItem> getVersions() {
        return versions;
    }

    public void setVersions(List<DocumentVersionItem> versions) {
        this.versions = versions;
    }
}
