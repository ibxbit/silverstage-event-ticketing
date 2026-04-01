package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.AppealDecisionRequest;
import com.eaglepoint.venue.api.dto.AuditLogResponse;
import com.eaglepoint.venue.api.dto.AppealRequest;
import com.eaglepoint.venue.api.dto.ContentResponse;
import com.eaglepoint.venue.api.dto.DiffResponse;
import com.eaglepoint.venue.api.dto.UpdateContentRequest;
import com.eaglepoint.venue.common.ContentState;
import com.eaglepoint.venue.domain.ContentAppeal;
import com.eaglepoint.venue.domain.ContentAuditLog;
import com.eaglepoint.venue.domain.ContentVersion;
import com.eaglepoint.venue.domain.PublishedContent;
import com.eaglepoint.venue.mapper.ContentAppealMapper;
import com.eaglepoint.venue.mapper.ContentAuditLogMapper;
import com.eaglepoint.venue.mapper.ContentVersionMapper;
import com.eaglepoint.venue.mapper.PublishedContentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishingWorkflowServiceTest {

    @Mock
    private PublishedContentMapper publishedContentMapper;
    @Mock
    private ContentVersionMapper contentVersionMapper;
    @Mock
    private ContentAppealMapper contentAppealMapper;
    @Mock
    private ContentAuditLogMapper contentAuditLogMapper;

    private PublishingWorkflowService publishingWorkflowService;

    @BeforeEach
    void setUp() {
        publishingWorkflowService = new PublishingWorkflowService(
                publishedContentMapper,
                contentVersionMapper,
                contentAppealMapper,
                contentAuditLogMapper
        );
    }

    @Test
    void publishingStateMachine_supportsAppealAndPostPublishCorrection() {
        AtomicReference<PublishedContent> contentRef = new AtomicReference<PublishedContent>();
        PublishedContent initial = new PublishedContent();
        initial.setId(1L);
        initial.setTitle("Initial title");
        initial.setBody("Initial body");
        initial.setState(ContentState.DRAFT.value());
        initial.setCurrentVersion(1);
        contentRef.set(initial);

        when(publishedContentMapper.findById(1L)).thenAnswer(invocation -> cloneContent(contentRef.get()));
        doAnswer(invocation -> {
            String state = invocation.getArgument(1);
            LocalDateTime publishedAt = invocation.getArgument(2);
            PublishedContent updated = cloneContent(contentRef.get());
            updated.setState(state);
            updated.setPublishedAt(publishedAt);
            contentRef.set(updated);
            return 1;
        }).when(publishedContentMapper).updateState(eq(1L), anyString(), nullable(LocalDateTime.class), any(LocalDateTime.class));
        doAnswer(invocation -> {
            String title = invocation.getArgument(1);
            String body = invocation.getArgument(2);
            Integer currentVersion = invocation.getArgument(3);
            PublishedContent updated = cloneContent(contentRef.get());
            updated.setTitle(title);
            updated.setBody(body);
            updated.setCurrentVersion(currentVersion);
            contentRef.set(updated);
            return 1;
        }).when(publishedContentMapper).updateBodyAndVersion(eq(1L), anyString(), anyString(), anyInt(), any(LocalDateTime.class));

        Map<Integer, ContentVersion> versions = new HashMap<Integer, ContentVersion>();
        doAnswer(invocation -> {
            ContentVersion row = invocation.getArgument(0);
            row.setId((long) row.getVersionNumber());
            row.setCreatedAt(LocalDateTime.now());
            versions.put(row.getVersionNumber(), row);
            return 1;
        }).when(contentVersionMapper).insert(any(ContentVersion.class));
        when(contentVersionMapper.findByContentIdAndVersion(eq(1L), anyInt())).thenAnswer(invocation -> {
            Integer versionNumber = invocation.getArgument(1);
            return versions.get(versionNumber);
        });

        AtomicReference<ContentAppeal> appealRef = new AtomicReference<ContentAppeal>();
        doAnswer(invocation -> {
            ContentAppeal appeal = invocation.getArgument(0);
            appeal.setId(22L);
            appealRef.set(appeal);
            return 1;
        }).when(contentAppealMapper).insert(any(ContentAppeal.class));
        when(contentAppealMapper.findById(22L)).thenAnswer(invocation -> appealRef.get());
        doAnswer(invocation -> {
            String status = invocation.getArgument(1);
            String reviewedBy = invocation.getArgument(2);
            String reviewNotes = invocation.getArgument(3);
            ContentAppeal current = appealRef.get();
            current.setStatus(status);
            current.setReviewedBy(reviewedBy);
            current.setReviewNotes(reviewNotes);
            appealRef.set(current);
            return 1;
        }).when(contentAppealMapper).resolve(eq(22L), anyString(), anyString(), anyString());

        ContentResponse submitted = publishingWorkflowService.submit(1L, "author_a");
        assertEquals(ContentState.SUBMISSION.value(), submitted.getState());

        ContentResponse inReview = publishingWorkflowService.markReview(1L, "reviewer_a");
        assertEquals(ContentState.REVIEW.value(), inReview.getState());

        ContentResponse published = publishingWorkflowService.publish(1L, "reviewer_a");
        assertEquals(ContentState.PUBLISH.value(), published.getState());

        AppealRequest appealRequest = new AppealRequest();
        appealRequest.setJustification("Fix typo in published section");
        publishingWorkflowService.requestAppeal(1L, "author_a", appealRequest);

        AppealDecisionRequest decision = new AppealDecisionRequest();
        decision.setStatus("APPROVED");
        decision.setReviewNotes("Correction allowed");
        publishingWorkflowService.decideAppeal(22L, "moderator_a", decision);

        UpdateContentRequest correction = new UpdateContentRequest();
        correction.setTitle("Corrected title");
        correction.setBody("Corrected body");
        correction.setSummary("Post-publish correction");

        ContentResponse corrected = publishingWorkflowService.applyPublishedCorrection(1L, 22L, "author_a", correction);
        assertEquals(ContentState.PUBLISH.value(), corrected.getState());
        assertEquals(Integer.valueOf(2), corrected.getCurrentVersion());
        assertEquals("Corrected title", corrected.getTitle());

        verify(contentAuditLogMapper, atLeastOnce()).insert(any());
    }

    @Test
    void rollback_rejectsVersionsOutsideThirtyDayRetention() {
        PublishedContent published = new PublishedContent();
        published.setId(7L);
        published.setState(ContentState.PUBLISH.value());
        published.setCurrentVersion(4);
        when(publishedContentMapper.findById(7L)).thenReturn(published);

        ContentVersion oldVersion = new ContentVersion();
        oldVersion.setContentId(7L);
        oldVersion.setVersionNumber(2);
        oldVersion.setTitle("Archived title");
        oldVersion.setBody("Archived body");
        oldVersion.setCreatedAt(LocalDateTime.now().minusDays(31));
        when(contentVersionMapper.findByContentIdAndVersion(7L, 2)).thenReturn(oldVersion);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> publishingWorkflowService.rollback(7L, 2, "admin_actor")
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void diff_returnsLineByLineContentForRequestedVersions() {
        ContentVersion left = new ContentVersion();
        left.setContentId(9L);
        left.setVersionNumber(1);
        left.setBody("line-one\nline-two");

        ContentVersion right = new ContentVersion();
        right.setContentId(9L);
        right.setVersionNumber(2);
        right.setBody("line-one\nline-two-updated\nline-three");

        when(contentVersionMapper.findByContentIdAndVersion(9L, 1)).thenReturn(left);
        when(contentVersionMapper.findByContentIdAndVersion(9L, 2)).thenReturn(right);

        DiffResponse diff = publishingWorkflowService.diff(9L, 1, 2);

        assertEquals(Arrays.asList("line-one", "line-two"), diff.getLeftLines());
        assertEquals(Arrays.asList("line-one", "line-two-updated", "line-three"), diff.getRightLines());
    }

    @Test
    void auditTrail_preservesMapperSequence() {
        PublishedContent content = new PublishedContent();
        content.setId(55L);
        when(publishedContentMapper.findById(55L)).thenReturn(content);

        ContentAuditLog first = new ContentAuditLog();
        first.setId(101L);
        first.setAction("SUBMIT");
        first.setChangedBy("author_x");
        first.setChangeDetail("Submitted for review");

        ContentAuditLog second = new ContentAuditLog();
        second.setId(102L);
        second.setAction("REVIEW");
        second.setChangedBy("moderator_y");
        second.setChangeDetail("Moved to review");

        when(contentAuditLogMapper.findByContentId(55L)).thenReturn(Arrays.asList(first, second));

        java.util.List<AuditLogResponse> logs = publishingWorkflowService.auditTrail(55L);

        assertEquals(2, logs.size());
        assertEquals("SUBMIT", logs.get(0).getAction());
        assertEquals("REVIEW", logs.get(1).getAction());
    }

    private PublishedContent cloneContent(PublishedContent source) {
        PublishedContent copy = new PublishedContent();
        copy.setId(source.getId());
        copy.setTitle(source.getTitle());
        copy.setBody(source.getBody());
        copy.setState(source.getState());
        copy.setCurrentVersion(source.getCurrentVersion());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setPublishedAt(source.getPublishedAt());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }
}
