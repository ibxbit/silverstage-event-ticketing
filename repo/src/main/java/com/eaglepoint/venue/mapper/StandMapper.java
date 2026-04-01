package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.Stand;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface StandMapper {
    List<Stand> findBySessionId(@Param("sessionId") Long sessionId);
}
