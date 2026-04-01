package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.ReportEvidence;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ReportEvidenceMapper {
    int insert(ReportEvidence evidence);

    List<ReportEvidence> findByReportId(@Param("reportId") Long reportId);
}
