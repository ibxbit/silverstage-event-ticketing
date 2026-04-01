package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.AppealDecisionRequest;
import com.eaglepoint.venue.api.dto.AppealRequest;
import com.eaglepoint.venue.api.dto.AuditLogResponse;
import com.eaglepoint.venue.api.dto.ContentResponse;
import com.eaglepoint.venue.api.dto.ContentVersionResponse;
import com.eaglepoint.venue.api.dto.CreateContentRequest;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PublishingWorkflowService {
    
    private static final Logger logger = LoggerFactory.getLogger(PublishingWorkflowService.class);
    
    private final PublishedContentMapper publishedContentMapper;
    private final ContentVersionMapper contentVersionMapper;
    private final ContentAppealMapper contentAppealMapper;
    private final ContentAuditLogMapper contentAuditLogMapper;

    public PublishingWorkflowService(
            PublishedContentMapper publishedContentMapper,
            ContentVersionMapper contentVersionMapper,
            ContentAppealMapper contentAppealMapper,
            ContentAuditLogMapper contentAuditLogMapper
    ) {
        this.publishedContentMapper = publishedContentMapper;
        this.contentVersionMapper = contentVersionMapper;
        this.contentAppealMapper = contentAppealMapper;
        this.contentAuditLogMapper = contentAuditLogMapper;
    }

    @Transactional
    public ContentResponse createDraft(String actor, CreateContentRequest request) {
        logger.info("Creating draft content for user: {}", actor);
        PublishedContent content = new PublishedContent();
        content.setTitle(clean(request.getTitle()));
        content.setBody(clean(request.getBody()));
        content.setState(ContentState.DRAFT.value());
        content.setCurrentVersion(1);
        content.setCreatedBy(clean(actor));
        publishedContentMapper.insert(content);

        ContentVersion version = insertVersion(content.getId(), 1, request.getTitle(), request.getBody(), actor, "CREATE", "Initial draft created");
        log(content.getId(), version.getId(), "CREATE_DRAFT", actor, "Created draft content");
        logger.info("Draft content created with ID: {} for user: {}", content.getId(), actor);

        return mapContent(publishedContentMapper.findById(content.getId()));
    }

    @Transactional
    public ContentResponse updateDraft(Long contentId, String actor, UpdateContentRequest request) {
        logger.info("Updating draft content ID: {} by user: {}", contentId, actor);
        PublishedContent content = mustContent(contentId);
        if (!ContentState.DRAFT.value().equals(content.getState())) {
            logger.warn("Attempt to update content ID: {} in invalid state: {} by user: {}", contentId, content.getState(), actor);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "content can be edited directly only in DRAFT state");
        }

        int nextVersion = content.getCurrentVersion() + 1;
        publishedContentMapper.updateBodyAndVersion(contentId, clean(request.getTitle()), clean(request.getBody()), nextVersion, LocalDateTime.now());
        ContentVersion version = insertVersion(contentId, nextVersion, request.getTitle(), request.getBody(), actor, "UPDATE", request.getSummary());
        log(contentId, version.getId(), "UPDATE_DRAFT", actor, request.getSummary());
        logger.info("Draft content ID: {} updated successfully by user: {}", contentId, actor);

        return mapContent(mustContent(contentId));
    }

    @Transactional
    public ContentResponse submit(Long contentId, String actor) {
        logger.info("Submitting draft content ID: {} for review by user: {}", contentId, actor);
        PublishedContent content = mustContent(contentId);
        assertState(content, ContentState.DRAFT);
        publishedContentMapper.updateState(contentId, ContentState.SUBMISSION.value(), content.getPublishedAt(), LocalDateTime.now());
        log(contentId, null, "SUBMIT", actor, "Submitted for moderation review");
        logger.info("Draft content ID: {} submitted for review by user: {}", contentId, actor);
        return mapContent(mustContent(contentId));
    }

    @Transactional
    public ContentResponse markReview(Long contentId, String actor) {
        logger.info("Marking content ID: {} as reviewed by user: {}", contentId, actor);
        PublishedContent content = mustContent(contentId);
        assertState(content, ContentState.SUBMISSION);
        publishedContentMapper.updateState(contentId, ContentState.REVIEW.value(), content.getPublishedAt(), LocalDateTime.now());
        log(contentId, null, "REVIEW", actor, "Moved to review state");
        logger.info("Content ID: {} marked as reviewed by user: {}", contentId, actor);
        return mapContent(mustContent(contentId));
    }

    @Transactional
    public ContentResponse publish(Long contentId, String actor) {
        logger.info("Publishing content ID: {} by user: {}", contentId, actor);
        PublishedContent content = mustContent(contentId);
        assertState(content, ContentState.REVIEW);
        publishedContentMapper.updateState(contentId, ContentState.PUBLISH.value(), LocalDateTime.now(), LocalDateTime.now());
        log(contentId, null, "PUBLISH", actor, "Content published");
        logger.info("Content ID: {} published successfully by user: {}", contentId, actor);
        return mapContent(mustContent(contentId));
    }

    @Transactional
    public ContentAppeal requestAppeal(Long contentId, String actor, AppealRequest request) {
        PublishedContent content = mustContent(contentId);
        if (!ContentState.PUBLISH.value().equals(content.getState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "appeal is required only for post-publish corrections");
        }

        ContentAppeal appeal = new ContentAppeal();
        appeal.setContentId(contentId);
        appeal.setRequestedBy(clean(actor));
        appeal.setJustification(clean(request.getJustification()));
        appeal.setStatus("PENDING");
        contentAppealMapper.insert(appeal);
        log(contentId, null, "APPEAL_REQUESTED", actor, appeal.getJustification());
        return contentAppealMapper.findById(appeal.getId());
    }

    @Transactional
    public ContentAppeal decideAppeal(Long appealId, String actor, AppealDecisionRequest request) {
        ContentAppeal appeal = contentAppealMapper.findById(appealId);
        if (appeal == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "appeal not found");
        }
        if (!"PENDING".equals(appeal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "appeal already resolved");
        }

        String status = clean(request.getStatus()).toUpperCase(Locale.ROOT);
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be APPROVED or REJECTED");
        }

        contentAppealMapper.resolve(appealId, status, clean(actor), clean(request.getReviewNotes()));
        log(appeal.getContentId(), null, "APPEAL_" + status, actor, request.getReviewNotes());
        return contentAppealMapper.findById(appealId);
    }

    @Transactional
    public ContentResponse applyPublishedCorrection(Long contentId, Long appealId, String actor, UpdateContentRequest request) {
        PublishedContent content = mustContent(contentId);
        if (!ContentState.PUBLISH.value().equals(content.getState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "published correction is only available for published content");
        }

        ContentAppeal appeal = contentAppealMapper.findById(appealId);
        if (appeal == null || !contentId.equals(appeal.getContentId()) || !"APPROVED".equals(appeal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "approved appeal is required before correction");
        }

        int nextVersion = content.getCurrentVersion() + 1;
        publishedContentMapper.updateBodyAndVersion(contentId, clean(request.getTitle()), clean(request.getBody()), nextVersion, LocalDateTime.now());
        ContentVersion version = insertVersion(contentId, nextVersion, request.getTitle(), request.getBody(), actor, "POST_PUBLISH_CORRECTION", request.getSummary());
        log(contentId, version.getId(), "CORRECTION_APPLIED", actor, request.getSummary());
        return mapContent(mustContent(contentId));
    }

    public List<ContentVersionResponse> versions(Long contentId) {
        mustContent(contentId);
        List<ContentVersion> versions = contentVersionMapper.findByContentId(contentId);
        List<ContentVersionResponse> rows = new ArrayList<ContentVersionResponse>();
        for (ContentVersion version : versions) {
            rows.add(mapVersion(version));
        }
        return rows;
    }

    public DiffResponse diff(Long contentId, Integer leftVersion, Integer rightVersion) {
        ContentVersion left = contentVersionMapper.findByContentIdAndVersion(contentId, leftVersion);
        ContentVersion right = contentVersionMapper.findByContentIdAndVersion(contentId, rightVersion);
        if (left == null || right == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "one or both versions were not found");
        }

        DiffResponse response = new DiffResponse();
        response.setContentId(contentId);
        response.setLeftVersion(leftVersion);
        response.setRightVersion(rightVersion);
        response.setLeftLines(splitLines(left.getBody()));
        response.setRightLines(splitLines(right.getBody()));
        return response;
    }

    @Transactional
    public ContentResponse rollback(Long contentId, Integer targetVersion, String actor) {
        PublishedContent content = mustContent(contentId);
        ContentVersion target = contentVersionMapper.findByContentIdAndVersion(contentId, targetVersion);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "target version not found");
        }
        if (target.getCreatedAt().isBefore(LocalDateTime.now().minusDays(30))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "rollback allowed only within 30-day retention window");
        }

        int nextVersion = content.getCurrentVersion() + 1;
        publishedContentMapper.updateBodyAndVersion(contentId, target.getTitle(), target.getBody(), nextVersion, LocalDateTime.now());
        ContentVersion rollbackVersion = insertVersion(contentId, nextVersion, target.getTitle(), target.getBody(), actor, "ROLLBACK", "Rollback to version " + targetVersion);
        log(contentId, rollbackVersion.getId(), "ROLLBACK", actor, "Rolled back to version " + targetVersion);
        return mapContent(mustContent(contentId));
    }

    public List<AuditLogResponse> auditTrail(Long contentId) {
        mustContent(contentId);
        List<ContentAuditLog> logs = contentAuditLogMapper.findByContentId(contentId);
        List<AuditLogResponse> rows = new ArrayList<AuditLogResponse>();
        for (ContentAuditLog log : logs) {
            AuditLogResponse row = new AuditLogResponse();
            row.setId(log.getId());
            row.setAction(log.getAction());
            row.setChangedBy(log.getChangedBy());
            row.setDetail(log.getChangeDetail());
            row.setChangedAt(log.getChangedAt());
            rows.add(row);
        }
        return rows;
    }

    public List<ContentResponse> listAll() {
        List<PublishedContent> all = publishedContentMapper.findAll();
        List<ContentResponse> rows = new ArrayList<ContentResponse>();
        for (PublishedContent content : all) {
            rows.add(mapContent(content));
        }
        return rows;
    }

    public String contentOwner(Long contentId) {
        return mustContent(contentId).getCreatedBy();
    }

    private ContentVersion insertVersion(Long contentId, Integer versionNumber, String title, String body, String actor, String changeType, String summary) {
        ContentVersion version = new ContentVersion();
        version.setContentId(contentId);
        version.setVersionNumber(versionNumber);
        version.setTitle(clean(title));
        version.setBody(clean(body));
        version.setChangedBy(clean(actor));
        version.setChangeType(changeType);
        version.setChangeSummary(clean(summary));
        contentVersionMapper.insert(version);
        return contentVersionMapper.findByContentIdAndVersion(contentId, versionNumber);
    }

    private void log(Long contentId, Long versionId, String action, String actor, String detail) {
        ContentAuditLog log = new ContentAuditLog();
        log.setContentId(contentId);
        log.setVersionId(versionId);
        log.setAction(action);
        log.setChangedBy(clean(actor));
        log.setChangeDetail(clean(detail));
        contentAuditLogMapper.insert(log);
    }

    private PublishedContent mustContent(Long contentId) {
        PublishedContent content = publishedContentMapper.findById(contentId);
        if (content == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "content not found");
        }
        return content;
    }

    private void assertState(PublishedContent content, ContentState expectedState) {
        if (!expectedState.value().equals(content.getState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "invalid state transition from " + content.getState());
        }
    }

    private ContentResponse mapContent(PublishedContent content) {
        ContentResponse response = new ContentResponse();
        response.setContentId(content.getId());
        response.setTitle(content.getTitle());
        response.setBody(content.getBody());
        response.setState(content.getState());
        response.setCurrentVersion(content.getCurrentVersion());
        response.setPublishedAt(content.getPublishedAt());
        return response;
    }

    private ContentVersionResponse mapVersion(ContentVersion version) {
        ContentVersionResponse row = new ContentVersionResponse();
        row.setVersionId(version.getId());
        row.setVersionNumber(version.getVersionNumber());
        row.setTitle(version.getTitle());
        row.setBody(version.getBody());
        row.setChangedBy(version.getChangedBy());
        row.setChangeType(version.getChangeType());
        row.setChangeSummary(version.getChangeSummary());
        row.setCreatedAt(version.getCreatedAt());
        return row;
    }

    private List<String> splitLines(String text) {
        List<String> lines = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        String[] arr = text.split("\\R", -1);
        for (String line : arr) {
            lines.add(line);
        }
        return lines;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
