package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.PaymentTransaction;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentTransactionMapper {
    int insert(PaymentTransaction row);
    PaymentTransaction findByTransactionRef(@Param("transactionRef") String transactionRef);
    List<PaymentTransaction> findAll();
    int updateStatus(@Param("id") Long id, @Param("status") String status);
    int incrementRefunded(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
