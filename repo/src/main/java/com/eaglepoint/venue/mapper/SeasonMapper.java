package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.Season;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SeasonMapper {
    List<Season> findByEventId(@Param("eventId") Long eventId);

    List<Season> findAll();
}
