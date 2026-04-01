package com.eaglepoint.venue.api;

import com.eaglepoint.venue.api.dto.DocumentHistoryResponse;
import com.eaglepoint.venue.api.dto.DocumentUploadResponse;
import com.eaglepoint.venue.api.dto.DownloadLinkResponse;
import com.eaglepoint.venue.api.dto.PagedDocumentsResponse;
import com.eaglepoint.venue.common.SecurityConstants;
import com.eaglepoint.venue.domain.UserAccount;
import com.eaglepoint.venue.service.FileManagementService;
import com.eaglepoint.venue.service.RequestAuthorizationService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileManagementController {

    private final FileManagementService fileManagementService;
    private final RequestAuthorizationService requestAuthorizationService;

    public FileManagementController(FileManagementService fileManagementService, RequestAuthorizationService requestAuthorizationService) {
        this.fileManagementService = fileManagementService;
        this.requestAuthorizationService = requestAuthorizationService;
    }

    @PostMapping("/upload")
    public DocumentUploadResponse uploadDocument(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @RequestParam String title,
            @RequestParam String folderPath,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false, defaultValue = "STAFF_AND_ADMIN") String accessLevel,
            @RequestParam("file") MultipartFile file
    ) {
        UserAccount user = requestAuthorizationService.requireAnyRole(
                token,
                SecurityConstants.ROLE_SERVICE_STAFF,
                SecurityConstants.ROLE_ORG_ADMIN,
                SecurityConstants.ROLE_PLATFORM_ADMIN
        );
        return fileManagementService.uploadNewDocument(user.getRole(), user.getUsername(), title, folderPath, tags, accessLevel, file);
    }

    @PostMapping("/{documentId}/versions")
    public DocumentUploadResponse uploadDocumentVersion(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable Long documentId,
            @RequestParam(required = false) String tags,
            @RequestParam("file") MultipartFile file
    ) {
        UserAccount user = requestAuthorizationService.requireAnyRole(
                token,
                SecurityConstants.ROLE_SERVICE_STAFF,
                SecurityConstants.ROLE_ORG_ADMIN,
                SecurityConstants.ROLE_PLATFORM_ADMIN
        );
        return fileManagementService.uploadNewVersion(user.getRole(), user.getUsername(), documentId, tags, file);
    }

    @GetMapping
    public PagedDocumentsResponse listDocuments(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @RequestParam(required = false) String folderPath,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size
    ) {
        UserAccount user = requestAuthorizationService.requireAuthenticated(token);
        return fileManagementService.listDocuments(user.getRole(), folderPath, tag, page, size);
    }

    @GetMapping("/{documentId}/history")
    public DocumentHistoryResponse history(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable Long documentId
    ) {
        UserAccount user = requestAuthorizationService.requireAuthenticated(token);
        return fileManagementService.versionHistory(user.getRole(), documentId);
    }

    @PostMapping("/{documentId}/download-links")
    public DownloadLinkResponse generateDownloadLink(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable Long documentId,
            @RequestParam(required = false, defaultValue = "72") Integer validHours
    ) {
        UserAccount user = requestAuthorizationService.requireAuthenticated(token);
        return fileManagementService.generateDownloadLink(user.getRole(), user.getUsername(), documentId, validHours);
    }

    @GetMapping("/download/{token}")
    public ResponseEntity<ByteArrayResource> downloadByToken(
            @RequestHeader(value = "X-Auth-Token", required = false) String authToken,
            @PathVariable String token
    ) {
        UserAccount user = requestAuthorizationService.requireAuthenticated(authToken);
        FileManagementService.DownloadPayload payload = fileManagementService.resolveDownload(user.getRole(), token);
        ByteArrayResource resource = new ByteArrayResource(payload.getBytes());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.getFileName() + "\"")
                .body(resource);
    }
}
