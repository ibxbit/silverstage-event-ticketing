package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.DocumentHistoryResponse;
import com.eaglepoint.venue.api.dto.DocumentListItem;
import com.eaglepoint.venue.api.dto.DocumentUploadResponse;
import com.eaglepoint.venue.api.dto.DocumentVersionItem;
import com.eaglepoint.venue.api.dto.DownloadLinkResponse;
import com.eaglepoint.venue.api.dto.PagedDocumentsResponse;
import com.eaglepoint.venue.common.SecurityConstants;
import com.eaglepoint.venue.domain.DocumentFolder;
import com.eaglepoint.venue.domain.ManagedDocument;
import com.eaglepoint.venue.domain.ManagedDocumentTag;
import com.eaglepoint.venue.domain.ManagedDocumentVersion;
import com.eaglepoint.venue.domain.ManagedDownloadLink;
import com.eaglepoint.venue.mapper.DocumentFolderMapper;
import com.eaglepoint.venue.mapper.ManagedDocumentMapper;
import com.eaglepoint.venue.mapper.ManagedDocumentTagMapper;
import com.eaglepoint.venue.mapper.ManagedDocumentVersionMapper;
import com.eaglepoint.venue.mapper.ManagedDownloadLinkMapper;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileManagementService {
    private final Set<String> allowedUploadContentTypes;
    private final long maxUploadBytes;
    private final DocumentFolderMapper documentFolderMapper;
    private final ManagedDocumentMapper managedDocumentMapper;
    private final ManagedDocumentVersionMapper managedDocumentVersionMapper;
    private final ManagedDocumentTagMapper managedDocumentTagMapper;
    private final ManagedDownloadLinkMapper managedDownloadLinkMapper;
    private final Path rootPath = Paths.get("local-storage", "docs");

    public FileManagementService(
            DocumentFolderMapper documentFolderMapper,
            ManagedDocumentMapper managedDocumentMapper,
            ManagedDocumentVersionMapper managedDocumentVersionMapper,
            ManagedDocumentTagMapper managedDocumentTagMapper,
            ManagedDownloadLinkMapper managedDownloadLinkMapper,
            @Value("${app.files.allowed-content-types:application/pdf,text/plain,text/csv,application/json,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,image/png,image/jpeg}") String allowedUploadContentTypes,
            @Value("${app.files.max-upload-bytes:10485760}") long maxUploadBytes
    ) {
        this.documentFolderMapper = documentFolderMapper;
        this.managedDocumentMapper = managedDocumentMapper;
        this.managedDocumentVersionMapper = managedDocumentVersionMapper;
        this.managedDocumentTagMapper = managedDocumentTagMapper;
        this.managedDownloadLinkMapper = managedDownloadLinkMapper;
        this.allowedUploadContentTypes = parseCsv(allowedUploadContentTypes);
        this.maxUploadBytes = maxUploadBytes;
    }

    @PostConstruct
    public void initStorage() {
        try {
            Files.createDirectories(rootPath);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "unable to initialize local file storage");
        }
    }

    @Transactional
    public DocumentUploadResponse uploadNewDocument(
            String role,
            String actor,
            String title,
            String folderPath,
            String tagsCsv,
            String accessLevel,
            MultipartFile file
    ) {
        assertUploadRole(role);
        assertFile(file);

        DocumentFolder folder = ensureFolder(normalizeFolder(folderPath));

        ManagedDocument document = new ManagedDocument();
        document.setFolderId(folder.getId());
        document.setTitle(clean(title));
        document.setAccessLevel(normalizeAccessLevel(accessLevel));
        document.setCreatedBy(clean(actor));
        managedDocumentMapper.insert(document);

        List<String> tags = normalizeTags(tagsCsv);
        replaceTags(document.getId(), tags);

        createVersion(document.getId(), 1, file, clean(actor));
        ManagedDocument saved = managedDocumentMapper.findById(document.getId());
        return toUploadResponse(saved, folder.getFolderPath(), tags, 1);
    }

    @Transactional
    public DocumentUploadResponse uploadNewVersion(
            String role,
            String actor,
            Long documentId,
            String tagsCsv,
            MultipartFile file
    ) {
        assertUploadRole(role);
        assertFile(file);

        ManagedDocument document = findDocument(documentId);
        assertRoleCanAccess(role, document.getAccessLevel());

        ManagedDocumentVersion latest = managedDocumentVersionMapper.findLatestByDocumentId(documentId);
        int nextVersion = latest == null ? 1 : latest.getVersionNumber() + 1;
        createVersion(documentId, nextVersion, file, clean(actor));

        if (tagsCsv != null) {
            replaceTags(documentId, normalizeTags(tagsCsv));
        }

        String folderPath = folderPathById(document.getFolderId());
        List<String> tags = managedDocumentTagMapper.findTagsByDocumentId(documentId);
        return toUploadResponse(document, folderPath, tags, nextVersion);
    }

    public PagedDocumentsResponse listDocuments(String role, String folderPath, String tag, Integer page, Integer size) {
        String normalizedRole = normalizeRole(role);
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? 10 : Math.min(size, 100);
        String normalizedFolder = folderPath == null ? "" : normalizeFolder(folderPath);
        String normalizedTag = tag == null ? "" : clean(tag).toLowerCase(Locale.ROOT);

        List<ManagedDocument> all = managedDocumentMapper.findAll();
        List<DocumentListItem> filtered = new ArrayList<DocumentListItem>();
        for (ManagedDocument document : all) {
            if (!canRead(normalizedRole, document.getAccessLevel())) {
                continue;
            }
            String folder = folderPathById(document.getFolderId());
            if (!normalizedFolder.isEmpty() && !folder.equals(normalizedFolder)) {
                continue;
            }
            List<String> tags = managedDocumentTagMapper.findTagsByDocumentId(document.getId());
            if (!normalizedTag.isEmpty() && !containsTag(tags, normalizedTag)) {
                continue;
            }

            ManagedDocumentVersion latest = managedDocumentVersionMapper.findLatestByDocumentId(document.getId());
            DocumentListItem item = new DocumentListItem();
            item.setDocumentId(document.getId());
            item.setTitle(document.getTitle());
            item.setFolderPath(folder);
            item.setAccessLevel(document.getAccessLevel());
            item.setLatestVersion(latest == null ? 0 : latest.getVersionNumber());
            item.setTags(tags);
            filtered.add(item);
        }

        int fromIndex = safePage * safeSize;
        int toIndex = Math.min(filtered.size(), fromIndex + safeSize);

        List<DocumentListItem> slice = new ArrayList<DocumentListItem>();
        if (fromIndex < filtered.size()) {
            slice = filtered.subList(fromIndex, toIndex);
        }

        PagedDocumentsResponse response = new PagedDocumentsResponse();
        response.setPage(safePage);
        response.setSize(safeSize);
        response.setTotal((long) filtered.size());
        response.setItems(new ArrayList<DocumentListItem>(slice));
        return response;
    }

    public DocumentHistoryResponse versionHistory(String role, Long documentId) {
        ManagedDocument document = findDocument(documentId);
        assertRoleCanAccess(role, document.getAccessLevel());

        DocumentHistoryResponse response = new DocumentHistoryResponse();
        response.setDocumentId(document.getId());
        response.setTitle(document.getTitle());
        response.setFolderPath(folderPathById(document.getFolderId()));
        response.setAccessLevel(document.getAccessLevel());
        response.setTags(managedDocumentTagMapper.findTagsByDocumentId(document.getId()));

        List<ManagedDocumentVersion> versions = managedDocumentVersionMapper.findByDocumentId(document.getId());
        List<DocumentVersionItem> mapped = new ArrayList<DocumentVersionItem>();
        for (ManagedDocumentVersion version : versions) {
            DocumentVersionItem item = new DocumentVersionItem();
            item.setVersionId(version.getId());
            item.setVersionNumber(version.getVersionNumber());
            item.setOriginalFileName(version.getOriginalFileName());
            item.setContentType(version.getContentType());
            item.setFileSize(version.getFileSize());
            item.setChecksum(version.getChecksum());
            item.setUploadedBy(version.getUploadedBy());
            item.setCreatedAt(version.getCreatedAt());
            mapped.add(item);
        }
        response.setVersions(mapped);
        return response;
    }

    @Transactional
    public DownloadLinkResponse generateDownloadLink(String role, String actor, Long documentId, Integer validHours) {
        ManagedDocument document = findDocument(documentId);
        assertRoleCanAccess(role, document.getAccessLevel());

        ManagedDocumentVersion latest = managedDocumentVersionMapper.findLatestByDocumentId(documentId);
        if (latest == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document has no uploaded versions");
        }

        int hours = validHours == null ? 72 : validHours;
        if (hours <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "validHours must be positive");
        }

        ManagedDownloadLink link = new ManagedDownloadLink();
        link.setDocumentId(documentId);
        link.setVersionId(latest.getId());
        link.setToken(UUID.randomUUID().toString().replace("-", ""));
        link.setExpiresAt(LocalDateTime.now().plusHours(hours));
        link.setCreatedBy(clean(actor));
        managedDownloadLinkMapper.insert(link);

        DownloadLinkResponse response = new DownloadLinkResponse();
        response.setDocumentId(documentId);
        response.setVersionId(latest.getId());
        response.setToken(link.getToken());
        response.setDownloadPath("/api/files/download/" + link.getToken());
        response.setExpiresAt(link.getExpiresAt());
        return response;
    }

    public DownloadPayload resolveDownload(String role, String token) {
        ManagedDownloadLink link = managedDownloadLinkMapper.findValidByToken(clean(token), LocalDateTime.now());
        if (link == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "download link is invalid or expired");
        }

        ManagedDocument document = findDocument(link.getDocumentId());
        assertRoleCanAccess(role, document.getAccessLevel());

        ManagedDocumentVersion version = managedDocumentVersionMapper.findById(link.getVersionId());
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document version not found");
        }

        Path path = rootPath.resolve(version.getStoredPath());
        if (!Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "local file not found");
        }

        try {
            byte[] bytes = Files.readAllBytes(path);
            DownloadPayload payload = new DownloadPayload();
            payload.setBytes(bytes);
            payload.setContentType(version.getContentType());
            payload.setFileName(version.getOriginalFileName());
            return payload;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "unable to read local file");
        }
    }

    private ManagedDocument findDocument(Long documentId) {
        ManagedDocument document = managedDocumentMapper.findById(documentId);
        if (document == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found");
        }
        return document;
    }

    private void createVersion(Long documentId, int versionNumber, MultipartFile file, String actor) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unable to read uploaded file");
        }

        String safeOriginalName = sanitizeFileName(file.getOriginalFilename());
        String storedRelativePath = documentId + "/v" + versionNumber + "/" + safeOriginalName;
        Path target = rootPath.resolve(storedRelativePath);

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to store local file");
        }

        ManagedDocumentVersion version = new ManagedDocumentVersion();
        version.setDocumentId(documentId);
        version.setVersionNumber(versionNumber);
        version.setStoredPath(storedRelativePath.replace('\\', '/'));
        version.setOriginalFileName(safeOriginalName);
        version.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        version.setFileSize((long) bytes.length);
        version.setChecksum(sha256(bytes));
        version.setUploadedBy(actor);
        managedDocumentVersionMapper.insert(version);
    }

    private String folderPathById(Long folderId) {
        DocumentFolder folder = documentFolderMapper.findById(folderId);
        return folder == null ? "/uncategorized" : folder.getFolderPath();
    }

    private DocumentFolder ensureFolder(String folderPath) {
        DocumentFolder folder = documentFolderMapper.findByPath(folderPath);
        if (folder != null) {
            return folder;
        }
        folder = new DocumentFolder();
        folder.setFolderPath(folderPath);
        documentFolderMapper.insert(folder);
        return folder;
    }

    private void replaceTags(Long documentId, List<String> tags) {
        managedDocumentTagMapper.deleteByDocumentId(documentId);
        Set<String> unique = new HashSet<String>(tags);
        for (String tag : unique) {
            ManagedDocumentTag row = new ManagedDocumentTag();
            row.setDocumentId(documentId);
            row.setTag(tag);
            managedDocumentTagMapper.insert(row);
        }
    }

    private DocumentUploadResponse toUploadResponse(ManagedDocument document, String folderPath, List<String> tags, int latestVersion) {
        DocumentUploadResponse response = new DocumentUploadResponse();
        response.setDocumentId(document.getId());
        response.setTitle(document.getTitle());
        response.setFolderPath(folderPath);
        response.setLatestVersion(latestVersion);
        response.setAccessLevel(document.getAccessLevel());
        response.setTags(tags);
        return response;
    }

    private boolean containsTag(List<String> tags, String filterTagLower) {
        for (String tag : tags) {
            if (tag != null && tag.toLowerCase(Locale.ROOT).equals(filterTagLower)) {
                return true;
            }
        }
        return false;
    }

    private void assertUploadRole(String role) {
        String normalized = normalizeRole(role);
        if (!SecurityConstants.ROLE_SERVICE_STAFF.equals(normalized)
                && !SecurityConstants.ROLE_ORG_ADMIN.equals(normalized)
                && !SecurityConstants.ROLE_PLATFORM_ADMIN.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only SERVICE_STAFF, ORG_ADMIN, or PLATFORM_ADMIN may upload files");
        }
    }

    private void assertRoleCanAccess(String role, String accessLevel) {
        if (!canRead(normalizeRole(role), normalizeAccessLevel(accessLevel))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "insufficient role permissions for this document");
        }
    }

    private boolean canRead(String role, String accessLevel) {
        if (SecurityConstants.ROLE_ORG_ADMIN.equals(role) || SecurityConstants.ROLE_PLATFORM_ADMIN.equals(role)) {
            return true;
        }
        if (SecurityConstants.ROLE_SERVICE_STAFF.equals(role)) {
            return "STAFF_ONLY".equals(accessLevel) || "STAFF_AND_ADMIN".equals(accessLevel);
        }
        return false;
    }

    private void assertFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file upload must not be empty");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file exceeds max allowed size");
        }
        String contentType = clean(file.getContentType()).toLowerCase(Locale.ROOT);
        if (!allowedUploadContentTypes.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported file content type");
        }
    }

    private Set<String> parseCsv(String csv) {
        Set<String> parsed = new HashSet<String>();
        String[] items = clean(csv).split(",");
        for (String item : items) {
            String value = clean(item).toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                parsed.add(value);
            }
        }
        return parsed;
    }

    private String normalizeRole(String role) {
        return clean(role).toUpperCase(Locale.ROOT);
    }

    private String normalizeAccessLevel(String accessLevel) {
        String normalized = clean(accessLevel).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "STAFF_AND_ADMIN";
        }
        if (!"STAFF_ONLY".equals(normalized) && !"ADMIN_ONLY".equals(normalized) && !"STAFF_AND_ADMIN".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accessLevel must be STAFF_ONLY, ADMIN_ONLY, or STAFF_AND_ADMIN");
        }
        return normalized;
    }

    private List<String> normalizeTags(String tagsCsv) {
        List<String> tags = new ArrayList<String>();
        if (tagsCsv == null || tagsCsv.trim().isEmpty()) {
            return tags;
        }
        String[] chunks = tagsCsv.split(",");
        for (String chunk : chunks) {
            String tag = clean(chunk).toLowerCase(Locale.ROOT);
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private String normalizeFolder(String folderPath) {
        String path = clean(folderPath);
        if (path.isEmpty()) {
            path = "/uncategorized";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path.replace("..", "");
    }

    private String sanitizeFileName(String originalFileName) {
        String name = clean(originalFileName);
        if (name.isEmpty()) {
            name = "attachment.bin";
        }
        return name.replace("..", "").replace("/", "_").replace("\\", "_");
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "hash algorithm unavailable");
        }
    }

    public static class DownloadPayload {
        private byte[] bytes;
        private String contentType;
        private String fileName;

        public byte[] getBytes() {
            return bytes;
        }

        public void setBytes(byte[] bytes) {
            this.bytes = bytes;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }
    }
}
