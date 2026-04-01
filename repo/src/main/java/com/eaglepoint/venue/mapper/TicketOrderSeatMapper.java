package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.TicketOrderSeat;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TicketOrderSeatMapper {
    int insert(TicketOrderSeat ticketOrderSeat);

    List<Long> findSeatIdsByOrderId(@Param("orderId") Long orderId);
}
