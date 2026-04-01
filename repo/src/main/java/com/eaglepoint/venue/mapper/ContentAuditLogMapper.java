package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.ContentAuditLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ContentAuditLogMapper {
    int insert(ContentAuditLog log);

    List<ContentAuditLog> findByContentId(@Param("contentId") Long contentId);
}
