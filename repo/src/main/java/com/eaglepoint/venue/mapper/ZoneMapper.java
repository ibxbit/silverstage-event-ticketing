package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.Zone;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ZoneMapper {
    List<Zone> findByStandId(@Param("standId") Long standId);

    List<Zone> findBySessionId(@Param("sessionId") Long sessionId);
}
