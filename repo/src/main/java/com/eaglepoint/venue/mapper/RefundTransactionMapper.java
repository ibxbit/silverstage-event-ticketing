package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.RefundTransaction;

import java.util.List;

public interface RefundTransactionMapper {
    int insert(RefundTransaction row);
    List<RefundTransaction> findAll();
}
