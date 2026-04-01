package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.SettlementCallback;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SettlementCallbackMapper {
    int insert(SettlementCallback row);
    SettlementCallback findByTransactionRef(@Param("transactionRef") String transactionRef);
    List<SettlementCallback> findAll();
}
