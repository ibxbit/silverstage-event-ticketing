package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.ModerationDecisionRequest;
import com.eaglepoint.venue.api.dto.NotificationItemResponse;
import com.eaglepoint.venue.api.dto.PenaltyStatusResponse;
import com.eaglepoint.venue.api.dto.ReportResponse;
import com.eaglepoint.venue.common.SecurityConstants;
import com.eaglepoint.venue.domain.ContentReport;
import com.eaglepoint.venue.domain.ReportEvidence;
import com.eaglepoint.venue.domain.UserNotification;
import com.eaglepoint.venue.domain.UserPenalty;
import com.eaglepoint.venue.mapper.ContentReportMapper;
import com.eaglepoint.venue.mapper.ReportEvidenceMapper;
import com.eaglepoint.venue.mapper.UserNotificationMapper;
import com.eaglepoint.venue.mapper.UserPenaltyMapper;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

@Service
public class ModerationService {
    private final Set<String> allowedEvidenceContentTypes;
    private final long maxEvidenceBytes;
    private final ContentReportMapper contentReportMapper;
    private final ReportEvidenceMapper reportEvidenceMapper;
    private final UserPenaltyMapper userPenaltyMapper;
    private final UserNotificationMapper userNotificationMapper;
    private final Path evidenceRoot = Paths.get("local-storage", "moderation-evidence");

    public ModerationService(
            ContentReportMapper contentReportMapper,
            ReportEvidenceMapper reportEvidenceMapper,
            UserPenaltyMapper userPenaltyMapper,
            UserNotificationMapper userNotificationMapper,
            @Value("${app.moderation.allowed-evidence-content-types:image/png,image/jpeg,application/pdf,text/plain}") String allowedEvidenceContentTypes,
            @Value("${app.moderation.max-evidence-bytes:5242880}") long maxEvidenceBytes
    ) {
        this.allowedEvidenceContentTypes = parseCsv(allowedEvidenceContentTypes);
        this.maxEvidenceBytes = maxEvidenceBytes;
        this.contentReportMapper = contentReportMapper;
        this.reportEvidenceMapper = reportEvidenceMapper;
        this.userPenaltyMapper = userPenaltyMapper;
        this.userNotificationMapper = userNotificationMapper;
    }

    @PostConstruct
    public void initStorage() {
        try {
            Files.createDirectories(evidenceRoot);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "unable to initialize moderation evidence storage");
        }
    }

    @Transactional
    public ReportResponse submitReport(
            String reporterUser,
            String reportedUser,
            String contentType,
            String contentRef,
            String reason,
            MultipartFile[] evidenceFiles
    ) {
        String reporter = clean(reporterUser);
        String reported = clean(reportedUser);
        String safeReason = clean(reason);
        if (reporter.isEmpty() || reported.isEmpty() || safeReason.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reporterUser, reportedUser and reason are required");
        }

        ContentReport report = new ContentReport();
        report.setReporterUser(reporter);
        report.setReportedUser(reported);
        report.setContentType(clean(contentType).toUpperCase(Locale.ROOT));
        report.setContentRef(clean(contentRef));
        report.setReason(safeReason);
        report.setStatus("OPEN");
        contentReportMapper.insert(report);

        List<String> evidenceNames = new ArrayList<String>();
        if (evidenceFiles != null) {
            for (MultipartFile file : evidenceFiles) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                evidenceNames.add(storeEvidence(report.getId(), file));
            }
        }

        notifyUser(reporter, "Your report #" + report.getId() + " has been submitted and is pending moderator review.", "REPORT_RECEIVED");

        ReportResponse response = new ReportResponse();
        response.setReportId(report.getId());
        response.setStatus("OPEN");
        response.setReporterUser(reporter);
        response.setReportedUser(reported);
        response.setReason(safeReason);
        response.setEvidenceFiles(evidenceNames);
        return response;
    }

    public List<ReportResponse> listOpenReports(String role) {
        assertModeratorRole(role);
        List<ContentReport> reports = contentReportMapper.findByStatus("OPEN");
        List<ReportResponse> mapped = new ArrayList<ReportResponse>();
        for (ContentReport report : reports) {
            mapped.add(toReportResponse(report));
        }
        return mapped;
    }

    @Transactional
    public ReportResponse resolveReport(
            String role,
            String moderatorUser,
            Long reportId,
            ModerationDecisionRequest request
    ) {
        assertModeratorRole(role);
        ContentReport report = contentReportMapper.findById(reportId);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "report not found");
        }
        if (!"OPEN".equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "report has already been resolved");
        }

        String penaltyType = normalizePenalty(request.getPenaltyType());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime penaltyEndsAt = penaltyEndTime(now, penaltyType);

        int updated = contentReportMapper.resolveReport(
                reportId,
                "RESOLVED",
                clean(moderatorUser),
                clean(request.getDecisionNotes()),
                penaltyType,
                penaltyEndsAt,
                now
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "failed to resolve report");
        }

        if (!"NO_ACTION".equals(penaltyType)) {
            UserPenalty penalty = new UserPenalty();
            penalty.setReportId(reportId);
            penalty.setUsername(report.getReportedUser());
            penalty.setPenaltyType(penaltyType);
            penalty.setStartsAt(now);
            penalty.setEndsAt(penaltyEndsAt);
            penalty.setActive("Y");
            userPenaltyMapper.insert(penalty);
        }

        notifyUser(
                report.getReporterUser(),
                "Report #" + reportId + " is resolved. Outcome: " + describePenalty(penaltyType, penaltyEndsAt) + ".",
                "REPORT_OUTCOME"
        );
        notifyUser(
                report.getReportedUser(),
                "Moderation decision for report #" + reportId + ": " + describePenalty(penaltyType, penaltyEndsAt) + ".",
                "PENALTY_OUTCOME"
        );

        ContentReport resolved = contentReportMapper.findById(reportId);
        return toReportResponse(resolved);
    }

    public List<PenaltyStatusResponse> penaltyStatus(String username) {
        List<UserPenalty> penalties = userPenaltyMapper.findActiveByUsername(clean(username), LocalDateTime.now());
        List<PenaltyStatusResponse> mapped = new ArrayList<PenaltyStatusResponse>();
        for (UserPenalty penalty : penalties) {
            PenaltyStatusResponse row = new PenaltyStatusResponse();
            row.setUsername(penalty.getUsername());
            row.setPenaltyType(penalty.getPenaltyType());
            row.setEndsAt(penalty.getEndsAt());
            row.setActive("Y".equals(penalty.getActive()));
            mapped.add(row);
        }
        return mapped;
    }

    public List<NotificationItemResponse> notifications(String username) {
        List<UserNotification> rows = userNotificationMapper.findByUsername(clean(username));
        List<NotificationItemResponse> mapped = new ArrayList<NotificationItemResponse>();
        for (UserNotification row : rows) {
            NotificationItemResponse item = new NotificationItemResponse();
            item.setId(row.getId());
            item.setMessage(row.getMessage());
            item.setType(row.getNotificationType());
            item.setReadFlag(row.getReadFlag());
            item.setCreatedAt(row.getCreatedAt());
            mapped.add(item);
        }
        return mapped;
    }

    @Transactional
    public void markNotificationRead(String actorUsername, String actorRole, Long notificationId) {
        String role = clean(actorRole).toUpperCase(Locale.ROOT);
        if (SecurityConstants.ROLE_MODERATOR.equals(role)
                || SecurityConstants.ROLE_ORG_ADMIN.equals(role)
                || SecurityConstants.ROLE_PLATFORM_ADMIN.equals(role)) {
            UserNotification target = userNotificationMapper.findById(notificationId);
            if (target == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "notification not found");
            }
            userNotificationMapper.markAsRead(notificationId);
            return;
        }

        int updated = userNotificationMapper.markAsReadByUsername(notificationId, clean(actorUsername));
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "notification ownership required");
        }
    }

    @Scheduled(fixedDelay = 300000)
    public void deactivateExpiredPenalties() {
        userPenaltyMapper.deactivateExpired(LocalDateTime.now());
    }

    private String storeEvidence(Long reportId, MultipartFile file) {
        validateEvidenceFile(file);
        String safeName = sanitizeFileName(file.getOriginalFilename());
        String relativePath = reportId + "/" + UUID.randomUUID().toString().replace("-", "") + "-" + safeName;
        Path target = evidenceRoot.resolve(relativePath);

        byte[] bytes;
        try {
            bytes = file.getBytes();
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "unable to store evidence file");
        }

        ReportEvidence evidence = new ReportEvidence();
        evidence.setReportId(reportId);
        evidence.setStoredPath(relativePath.replace('\\', '/'));
        evidence.setOriginalFileName(safeName);
        evidence.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        evidence.setFileSize((long) bytes.length);
        reportEvidenceMapper.insert(evidence);
        return safeName;
    }

    private ReportResponse toReportResponse(ContentReport report) {
        ReportResponse response = new ReportResponse();
        response.setReportId(report.getId());
        response.setStatus(report.getStatus());
        response.setReporterUser(report.getReporterUser());
        response.setReportedUser(report.getReportedUser());
        response.setReason(report.getReason());
        response.setPenaltyType(report.getPenaltyType());
        response.setPenaltyEndsAt(report.getPenaltyEndsAt());

        List<ReportEvidence> evidenceRows = reportEvidenceMapper.findByReportId(report.getId());
        List<String> fileNames = new ArrayList<String>();
        for (ReportEvidence evidence : evidenceRows) {
            fileNames.add(evidence.getOriginalFileName());
        }
        response.setEvidenceFiles(fileNames);
        return response;
    }

    private void notifyUser(String username, String message, String type) {
        UserNotification notification = new UserNotification();
        notification.setUsername(username);
        notification.setMessage(message);
        notification.setNotificationType(type);
        notification.setReadFlag("N");
        userNotificationMapper.insert(notification);
    }

    private void assertModeratorRole(String role) {
        String normalized = clean(role).toUpperCase(Locale.ROOT);
        if (!SecurityConstants.ROLE_MODERATOR.equals(normalized)
                && !SecurityConstants.ROLE_ORG_ADMIN.equals(normalized)
                && !SecurityConstants.ROLE_PLATFORM_ADMIN.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "moderator role required");
        }
    }

    private void validateEvidenceFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "evidence file must not be empty");
        }
        if (file.getSize() > maxEvidenceBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "evidence file exceeds max allowed size");
        }
        String contentType = clean(file.getContentType()).toLowerCase(Locale.ROOT);
        if (!allowedEvidenceContentTypes.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported evidence content type");
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

    private String normalizePenalty(String penaltyType) {
        String normalized = clean(penaltyType).toUpperCase(Locale.ROOT);
        if (!"MUTE_24H".equals(normalized) && !"POST_RESTRICT_7D".equals(normalized)
                && !"PERMANENT_BAN".equals(normalized) && !"NO_ACTION".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid penaltyType");
        }
        return normalized;
    }

    private LocalDateTime penaltyEndTime(LocalDateTime now, String penaltyType) {
        if ("MUTE_24H".equals(penaltyType)) {
            return now.plusHours(24);
        }
        if ("POST_RESTRICT_7D".equals(penaltyType)) {
            return now.plusDays(7);
        }
        if ("PERMANENT_BAN".equals(penaltyType)) {
            return null;
        }
        return now;
    }

    private String describePenalty(String penaltyType, LocalDateTime endsAt) {
        if ("NO_ACTION".equals(penaltyType)) {
            return "No penalty applied";
        }
        if ("PERMANENT_BAN".equals(penaltyType)) {
            return "Permanent account ban";
        }
        return penaltyType + " until " + endsAt;
    }

    private String sanitizeFileName(String value) {
        String safe = clean(value);
        if (safe.isEmpty()) {
            safe = "evidence.bin";
        }
        return safe.replace("..", "").replace("/", "_").replace("\\", "_");
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
