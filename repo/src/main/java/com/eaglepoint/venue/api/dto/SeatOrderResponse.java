package com.eaglepoint.venue.api.dto;

import java.time.LocalDateTime;

public class SeatOrderResponse {
    private Long orderId;
    private String orderCode;
    private String status;
    private Integer quantity;
    private LocalDateTime holdExpiresAt;
    private LocalDateTime cancelExpiresAt;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public LocalDateTime getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public void setHoldExpiresAt(LocalDateTime holdExpiresAt) {
        this.holdExpiresAt = holdExpiresAt;
    }

    public LocalDateTime getCancelExpiresAt() {
        return cancelExpiresAt;
    }

    public void setCancelExpiresAt(LocalDateTime cancelExpiresAt) {
        this.cancelExpiresAt = cancelExpiresAt;
    }
}
