package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.TicketOrder;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TicketOrderMapper {
    int insert(TicketOrder ticketOrder);

    TicketOrder findById(@Param("id") Long id);

    List<TicketOrder> findExpiredSeatHolds(@Param("now") LocalDateTime now);

    List<TicketOrder> findAutoCancellableOrders(@Param("now") LocalDateTime now);

    int markHoldReleased(@Param("id") Long id);

    int markPaid(@Param("id") Long id);

    int markCancelledAndInventoryReturned(@Param("id") Long id);
}
