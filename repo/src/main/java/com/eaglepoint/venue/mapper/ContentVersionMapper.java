package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.ContentVersion;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ContentVersionMapper {
    int insert(ContentVersion contentVersion);

    ContentVersion findByContentIdAndVersion(@Param("contentId") Long contentId, @Param("versionNumber") Integer versionNumber);

    ContentVersion findLatestByContentId(@Param("contentId") Long contentId);

    List<ContentVersion> findByContentId(@Param("contentId") Long contentId);
}
