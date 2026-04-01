package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.TicketInventory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TicketInventoryMapper {
    int insert(TicketInventory ticketInventory);

    List<TicketInventory> findByTicketTypeId(@Param("ticketTypeId") Long ticketTypeId);

    TicketInventory findByTicketTypeIdAndChannel(@Param("ticketTypeId") Long ticketTypeId, @Param("channel") String channel);

    int incrementSoldWithinQuota(@Param("ticketTypeId") Long ticketTypeId, @Param("channel") String channel, @Param("quantity") Integer quantity);

    int decrementSold(@Param("ticketTypeId") Long ticketTypeId, @Param("channel") String channel, @Param("quantity") Integer quantity);
}
