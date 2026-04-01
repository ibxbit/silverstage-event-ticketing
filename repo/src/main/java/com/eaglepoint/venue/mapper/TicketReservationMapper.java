package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.TicketReservation;
import org.apache.ibatis.annotations.Param;

public interface TicketReservationMapper {
    int insert(TicketReservation ticketReservation);

    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
