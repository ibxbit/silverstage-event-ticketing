package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.DocumentHistoryResponse;
import com.eaglepoint.venue.api.dto.DocumentUploadResponse;
import com.eaglepoint.venue.api.dto.DownloadLinkResponse;
import com.eaglepoint.venue.common.SecurityConstants;
import com.eaglepoint.venue.domain.DocumentFolder;
import com.eaglepoint.venue.domain.ManagedDocument;
import com.eaglepoint.venue.domain.ManagedDocumentVersion;
import com.eaglepoint.venue.domain.ManagedDownloadLink;
import com.eaglepoint.venue.mapper.DocumentFolderMapper;
import com.eaglepoint.venue.mapper.ManagedDocumentMapper;
import com.eaglepoint.venue.mapper.ManagedDocumentTagMapper;
import com.eaglepoint.venue.mapper.ManagedDocumentVersionMapper;
import com.eaglepoint.venue.mapper.ManagedDownloadLinkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileManagementServiceTest {

    @Mock
    private DocumentFolderMapper documentFolderMapper;
    @Mock
    private ManagedDocumentMapper managedDocumentMapper;
    @Mock
    private ManagedDocumentVersionMapper managedDocumentVersionMapper;
    @Mock
    private ManagedDocumentTagMapper managedDocumentTagMapper;
    @Mock
    private ManagedDownloadLinkMapper managedDownloadLinkMapper;

    private FileManagementService fileManagementService;

    @BeforeEach
    void setUp() {
        fileManagementService = new FileManagementService(
                documentFolderMapper,
                managedDocumentMapper,
                managedDocumentVersionMapper,
                managedDocumentTagMapper,
                managedDownloadLinkMapper,
                "application/pdf,text/plain",
                100
        );
    }

    @Test
    void uploadNewDocument_createsFolderWhenMissing() {
        AtomicReference<DocumentFolder> folderRef = new AtomicReference<DocumentFolder>();
        when(documentFolderMapper.findByPath("/ops/reports")).thenAnswer(invocation -> folderRef.get());
        doAnswer(invocation -> {
            DocumentFolder folder = invocation.getArgument(0);
            folder.setId(5L);
            folderRef.set(folder);
            return 1;
        }).when(documentFolderMapper).insert(any(DocumentFolder.class));

        AtomicReference<ManagedDocument> documentRef = new AtomicReference<ManagedDocument>();
        doAnswer(invocation -> {
            ManagedDocument document = invocation.getArgument(0);
            document.setId(11L);
            documentRef.set(document);
            return 1;
        }).when(managedDocumentMapper).insert(any(ManagedDocument.class));
        when(managedDocumentMapper.findById(11L)).thenAnswer(invocation -> documentRef.get());

        doAnswer(invocation -> 1).when(managedDocumentVersionMapper).insert(any(ManagedDocumentVersion.class));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "weekly.txt",
                "text/plain",
                "weekly report".getBytes(StandardCharsets.UTF_8)
        );

        DocumentUploadResponse response = fileManagementService.uploadNewDocument(
                SecurityConstants.ROLE_SERVICE_STAFF,
                "staff_user",
                "Weekly Report",
                "ops/reports",
                "finance,internal",
                "STAFF_AND_ADMIN",
                file
        );

        assertEquals(Long.valueOf(11L), response.getDocumentId());
        assertEquals("/ops/reports", response.getFolderPath());
        assertEquals(Integer.valueOf(1), response.getLatestVersion());
        verify(documentFolderMapper).insert(any(DocumentFolder.class));
    }

    @Test
    void versionHistory_returnsVersionTimeline() {
        ManagedDocument document = new ManagedDocument();
        document.setId(21L);
        document.setFolderId(3L);
        document.setTitle("Policy Handbook");
        document.setAccessLevel("STAFF_AND_ADMIN");

        DocumentFolder folder = new DocumentFolder();
        folder.setId(3L);
        folder.setFolderPath("/handbook");

        ManagedDocumentVersion version1 = new ManagedDocumentVersion();
        version1.setId(100L);
        version1.setVersionNumber(1);
        version1.setOriginalFileName("policy-v1.pdf");

        ManagedDocumentVersion version2 = new ManagedDocumentVersion();
        version2.setId(101L);
        version2.setVersionNumber(2);
        version2.setOriginalFileName("policy-v2.pdf");

        when(managedDocumentMapper.findById(21L)).thenReturn(document);
        when(documentFolderMapper.findById(3L)).thenReturn(folder);
        when(managedDocumentTagMapper.findTagsByDocumentId(21L)).thenReturn(Arrays.asList("policy", "staff"));
        when(managedDocumentVersionMapper.findByDocumentId(21L)).thenReturn(Arrays.asList(version1, version2));

        DocumentHistoryResponse history = fileManagementService.versionHistory(SecurityConstants.ROLE_SERVICE_STAFF, 21L);

        assertEquals("Policy Handbook", history.getTitle());
        assertEquals(2, history.getVersions().size());
        assertEquals(Integer.valueOf(1), history.getVersions().get(0).getVersionNumber());
        assertEquals(Integer.valueOf(2), history.getVersions().get(1).getVersionNumber());
    }

    @Test
    void generateDownloadLink_createsExpiringTokenForLatestVersion() {
        ManagedDocument document = new ManagedDocument();
        document.setId(31L);
        document.setFolderId(9L);
        document.setAccessLevel("STAFF_AND_ADMIN");

        ManagedDocumentVersion latest = new ManagedDocumentVersion();
        latest.setId(701L);
        latest.setDocumentId(31L);
        latest.setVersionNumber(5);

        when(managedDocumentMapper.findById(31L)).thenReturn(document);
        when(managedDocumentVersionMapper.findLatestByDocumentId(31L)).thenReturn(latest);

        ArgumentCaptor<ManagedDownloadLink> linkCaptor = ArgumentCaptor.forClass(ManagedDownloadLink.class);
        doAnswer(invocation -> 1).when(managedDownloadLinkMapper).insert(linkCaptor.capture());

        DownloadLinkResponse response = fileManagementService.generateDownloadLink(
                SecurityConstants.ROLE_SERVICE_STAFF,
                "staff_user",
                31L,
                2
        );

        assertEquals(Long.valueOf(31L), response.getDocumentId());
        assertEquals(Long.valueOf(701L), response.getVersionId());
        assertNotNull(response.getToken());
        assertTrue(response.getDownloadPath().contains(response.getToken()));

        ManagedDownloadLink persisted = linkCaptor.getValue();
        assertEquals(Long.valueOf(31L), persisted.getDocumentId());
        assertEquals(Long.valueOf(701L), persisted.getVersionId());
        assertTrue(persisted.getExpiresAt().isAfter(LocalDateTime.now().plusHours(1)));
        assertTrue(persisted.getExpiresAt().isBefore(LocalDateTime.now().plusHours(3)));
    }

    @Test
    void uploadNewDocument_rejectsUnsupportedMimeType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.sh",
                "application/x-sh",
                "echo bad".getBytes(StandardCharsets.UTF_8)
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> fileManagementService.uploadNewDocument(
                        SecurityConstants.ROLE_SERVICE_STAFF,
                        "staff_user",
                        "Invalid",
                        "/ops",
                        null,
                        "STAFF_AND_ADMIN",
                        file
                )
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void uploadNewDocument_rejectsOversizedFile() {
        byte[] oversized = new byte[101];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "too-large.txt",
                "text/plain",
                oversized
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> fileManagementService.uploadNewDocument(
                        SecurityConstants.ROLE_SERVICE_STAFF,
                        "staff_user",
                        "TooLarge",
                        "/ops",
                        null,
                        "STAFF_AND_ADMIN",
                        file
                )
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }
}
