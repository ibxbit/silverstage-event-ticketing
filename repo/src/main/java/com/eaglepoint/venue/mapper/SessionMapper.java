package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.SessionEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SessionMapper {
    List<SessionEntity> findBySeasonId(@Param("seasonId") Long seasonId);

    SessionEntity findById(@Param("id") Long id);

    List<SessionEntity> findAll();
}
