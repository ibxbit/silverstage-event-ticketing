package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.AppealDecisionRequest;
import com.eaglepoint.venue.api.dto.AppealRequest;
import com.eaglepoint.venue.api.dto.ContentResponse;
import com.eaglepoint.venue.api.dto.UpdateContentRequest;
import com.eaglepoint.venue.common.ContentState;
import com.eaglepoint.venue.domain.PublishedContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Rollback
class PublishingWorkflowIntegrationTest {

    @Autowired
    private PublishingWorkflowService publishingWorkflowService;

    private Long contentId;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:publishingtest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("app.security.aes-key", () -> "test-key-1234567890123456789012"); // 32 chars
    }

    @BeforeEach
    void setUp() {
        // Create a draft content for testing
        ContentResponse draft = publishingWorkflowService.createDraft("test_user", 
            new com.eaglepoint.venue.api.dto.CreateContentRequest() {{
                setTitle("Test Content");
                setBody("Test Body");
            }});
        contentId = draft.getContentId();
    }

    @Test
    void testPublishingWorkflowStateTransitionsAndOwnership() {
        // Initial state should be DRAFT
        ContentResponse content = publishingWorkflowService.listAll().get(0);
        assertEquals(ContentState.DRAFT.value(), content.getState());
        assertEquals("Test Content", content.getTitle());

        // Test update by owner (should succeed)
        UpdateContentRequest updateRequest = new UpdateContentRequest();
        updateRequest.setTitle("Updated Title");
        updateRequest.setBody("Updated Body");
        updateRequest.setSummary("Updated content");

        ContentResponse updated = publishingWorkflowService.updateDraft(contentId, "test_user", updateRequest);
        assertEquals("Updated Title", updated.getTitle());
        assertEquals(ContentState.DRAFT.value(), updated.getState());

        // Test submit by owner (should succeed)
        ContentResponse submitted = publishingWorkflowService.submit(contentId, "test_user");
        assertEquals(ContentState.SUBMISSION.value(), submitted.getState());

        // Test review by moderator (should succeed)
        ContentResponse reviewed = publishingWorkflowService.markReview(contentId, "moderator");
        assertEquals(ContentState.REVIEW.value(), reviewed.getState());

        // Test publish by moderator (should succeed)
        ContentResponse published = publishingWorkflowService.publish(contentId, "moderator");
        assertEquals(ContentState.PUBLISH.value(), published.getState());

        // Test appeal request by owner (should succeed)
        AppealRequest appealRequest = new AppealRequest();
        appealRequest.setJustification("Need to fix typo");
        publishingWorkflowService.requestAppeal(contentId, "test_user", appealRequest);

        // Test appeal decision by moderator (should succeed)
        AppealDecisionRequest decision = new AppealDecisionRequest();
        decision.setStatus("APPROVED");
        decision.setReviewNotes("Correction allowed");
        // Note: We're not asserting on the appeal ID since it's auto-generated
        publishingWorkflowService.decideAppeal(1L, "moderator", decision); // Assuming appeal ID is 1

        // Test post-publish correction by owner (should succeed)
        UpdateContentRequest correctionRequest = new UpdateContentRequest();
        correctionRequest.setTitle("Corrected Title");
        correctionRequest.setBody("Corrected Body");
        correctionRequest.setSummary("Post-publish correction");
        ContentResponse corrected = publishingWorkflowService.applyPublishedCorrection(contentId, 1L, "test_user", correctionRequest);
        assertEquals("Corrected Title", corrected.getTitle());
        assertEquals(ContentState.PUBLISH.value(), corrected.getState());

        // Test that non-owner cannot update draft (unless privileged role)
        assertThrows(Exception.class, () -> {
            publishingWorkflowService.updateDraft(contentId, "other_user", updateRequest);
        });

        // Once published, direct draft updates should remain blocked.
        assertThrows(Exception.class, () -> {
            publishingWorkflowService.updateDraft(contentId, "moderator", updateRequest);
        });
    }
}
