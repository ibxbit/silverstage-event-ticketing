package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.ContentAppeal;
import org.apache.ibatis.annotations.Param;

public interface ContentAppealMapper {
    int insert(ContentAppeal contentAppeal);

    ContentAppeal findById(@Param("id") Long id);

    int resolve(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("reviewedBy") String reviewedBy,
            @Param("reviewNotes") String reviewNotes
    );
}
