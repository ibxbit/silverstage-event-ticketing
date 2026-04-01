package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.PublishedContent;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PublishedContentMapper {
    int insert(PublishedContent content);

    PublishedContent findById(@Param("id") Long id);

    List<PublishedContent> findAll();

    int updateBodyAndVersion(
            @Param("id") Long id,
            @Param("title") String title,
            @Param("body") String body,
            @Param("currentVersion") Integer currentVersion,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int updateState(
            @Param("id") Long id,
            @Param("state") String state,
            @Param("publishedAt") LocalDateTime publishedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
