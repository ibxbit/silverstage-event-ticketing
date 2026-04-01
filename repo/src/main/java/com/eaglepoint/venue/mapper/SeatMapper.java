package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.Seat;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SeatMapper {
    List<Seat> findByZoneId(@Param("zoneId") Long zoneId);

    List<Seat> findBySessionId(@Param("sessionId") Long sessionId);

    int updateSeatStatusForIds(
            @Param("seatIds") List<Long> seatIds,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus
    );
}
