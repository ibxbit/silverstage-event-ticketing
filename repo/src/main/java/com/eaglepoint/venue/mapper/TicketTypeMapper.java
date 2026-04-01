package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.TicketType;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TicketTypeMapper {
    int insert(TicketType ticketType);

    TicketType findById(@Param("id") Long id);

    List<TicketType> findByEventId(@Param("eventId") Long eventId);
}
