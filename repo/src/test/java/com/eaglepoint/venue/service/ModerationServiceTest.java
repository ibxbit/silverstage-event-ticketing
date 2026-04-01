package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.ModerationDecisionRequest;
import com.eaglepoint.venue.api.dto.ReportResponse;
import com.eaglepoint.venue.common.SecurityConstants;
import com.eaglepoint.venue.domain.ContentReport;
import com.eaglepoint.venue.domain.UserNotification;
import com.eaglepoint.venue.domain.UserPenalty;
import com.eaglepoint.venue.mapper.ContentReportMapper;
import com.eaglepoint.venue.mapper.ReportEvidenceMapper;
import com.eaglepoint.venue.mapper.UserNotificationMapper;
import com.eaglepoint.venue.mapper.UserPenaltyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock
    private ContentReportMapper contentReportMapper;
    @Mock
    private ReportEvidenceMapper reportEvidenceMapper;
    @Mock
    private UserPenaltyMapper userPenaltyMapper;
    @Mock
    private UserNotificationMapper userNotificationMapper;

    private ModerationService moderationService;

    @BeforeEach
    void setUp() {
        moderationService = new ModerationService(
                contentReportMapper,
                reportEvidenceMapper,
                userPenaltyMapper,
                userNotificationMapper,
                "image/png,application/pdf",
                1024
        );
    }

    @Test
    void submitReport_createsOpenReportAndReporterNotification() {
        doAnswer(invocation -> {
            ContentReport report = invocation.getArgument(0);
            report.setId(42L);
            return 1;
        }).when(contentReportMapper).insert(any(ContentReport.class));

        ReportResponse response = moderationService.submitReport(
                "reporter_a",
                "reported_b",
                "COMMENT",
                "CMT-100",
                "abusive text",
                null
        );

        assertEquals(Long.valueOf(42L), response.getReportId());
        assertEquals("OPEN", response.getStatus());
        assertEquals("reporter_a", response.getReporterUser());
        verify(userNotificationMapper).insert(any(UserNotification.class));
    }

    @Test
    void resolveReport_appliesMutePenaltyAndGeneratesNotifications() {
        ContentReport open = new ContentReport();
        open.setId(77L);
        open.setStatus("OPEN");
        open.setReporterUser("reporter_c");
        open.setReportedUser("target_user");
        open.setReason("spam");

        ContentReport resolved = new ContentReport();
        resolved.setId(77L);
        resolved.setStatus("RESOLVED");
        resolved.setReporterUser("reporter_c");
        resolved.setReportedUser("target_user");
        resolved.setPenaltyType("MUTE_24H");

        when(contentReportMapper.findById(77L)).thenReturn(open, resolved);
        when(contentReportMapper.resolveReport(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(reportEvidenceMapper.findByReportId(77L)).thenReturn(java.util.Collections.emptyList());

        ModerationDecisionRequest decision = new ModerationDecisionRequest();
        decision.setPenaltyType("MUTE_24H");
        decision.setDecisionNotes("Repeated violations");

        ReportResponse response = moderationService.resolveReport(
                SecurityConstants.ROLE_MODERATOR,
                "mod_1",
                77L,
                decision
        );

        assertEquals("RESOLVED", response.getStatus());
        assertEquals("MUTE_24H", response.getPenaltyType());

        ArgumentCaptor<UserPenalty> penaltyCaptor = ArgumentCaptor.forClass(UserPenalty.class);
        verify(userPenaltyMapper).insert(penaltyCaptor.capture());
        UserPenalty penalty = penaltyCaptor.getValue();
        assertEquals("target_user", penalty.getUsername());
        assertEquals("MUTE_24H", penalty.getPenaltyType());
        assertTrue(penalty.getEndsAt().isAfter(penalty.getStartsAt().plusHours(23)));

        verify(userNotificationMapper, times(2)).insert(any(UserNotification.class));
    }

    @Test
    void markNotificationRead_ownerCanMarkOwnNotification() {
        when(userNotificationMapper.markAsReadByUsername(9L, "owner_a")).thenReturn(1);

        moderationService.markNotificationRead("owner_a", SecurityConstants.ROLE_SENIOR, 9L);

        verify(userNotificationMapper).markAsReadByUsername(9L, "owner_a");
        verify(userNotificationMapper, never()).markAsRead(9L);
    }

    @Test
    void markNotificationRead_nonOwnerIsForbidden() {
        when(userNotificationMapper.markAsReadByUsername(10L, "user_a")).thenReturn(0);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> moderationService.markNotificationRead("user_a", SecurityConstants.ROLE_SENIOR, 10L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(userNotificationMapper, never()).markAsRead(10L);
    }

    @Test
    void markNotificationRead_privilegedRoleCanMarkAnyNotification() {
        UserNotification row = new UserNotification();
        row.setId(12L);
        row.setUsername("user_b");
        when(userNotificationMapper.findById(12L)).thenReturn(row);

        moderationService.markNotificationRead("mod_1", SecurityConstants.ROLE_MODERATOR, 12L);

        verify(userNotificationMapper).findById(12L);
        verify(userNotificationMapper).markAsRead(12L);
        verify(userNotificationMapper, never()).markAsReadByUsername(12L, "mod_1");
    }

    @Test
    void markNotificationRead_privilegedRoleMissingNotificationReturns404() {
        when(userNotificationMapper.findById(13L)).thenReturn(null);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> moderationService.markNotificationRead("admin_a", SecurityConstants.ROLE_ORG_ADMIN, 13L)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(userNotificationMapper).findById(13L);
        verify(userNotificationMapper, never()).markAsRead(13L);
    }
}
