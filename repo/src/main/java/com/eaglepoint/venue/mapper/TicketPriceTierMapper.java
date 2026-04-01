package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.TicketPriceTier;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface TicketPriceTierMapper {
    int insert(TicketPriceTier ticketPriceTier);

    List<TicketPriceTier> findByTicketTypeId(@Param("ticketTypeId") Long ticketTypeId);

    BigDecimal findApplicablePrice(@Param("ticketTypeId") Long ticketTypeId, @Param("quantity") Integer quantity);
}
