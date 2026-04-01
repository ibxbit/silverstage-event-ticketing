package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.ContentReport;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ContentReportMapper {
    int insert(ContentReport report);

    ContentReport findById(@Param("id") Long id);

    List<ContentReport> findByStatus(@Param("status") String status);

    int resolveReport(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("moderatorUser") String moderatorUser,
            @Param("decisionNotes") String decisionNotes,
            @Param("penaltyType") String penaltyType,
            @Param("penaltyEndsAt") LocalDateTime penaltyEndsAt,
            @Param("resolvedAt") LocalDateTime resolvedAt
    );
}
