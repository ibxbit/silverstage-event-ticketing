package com.eaglepoint.venue.domain;

import java.time.LocalDateTime;

public class TicketOrder {
    private Long id;
    private Long eventId;
    private Long sessionId;
    private Long ticketTypeId;
    private String orderCode;
    private String buyerReference;
    private String channel;
    private Integer quantity;
    private String status;
    private LocalDateTime holdExpiresAt;
    private LocalDateTime cancelExpiresAt;
    private Integer inventoryReturned;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getTicketTypeId() {
        return ticketTypeId;
    }

    public void setTicketTypeId(Long ticketTypeId) {
        this.ticketTypeId = ticketTypeId;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode;
    }

    public String getBuyerReference() {
        return buyerReference;
    }

    public void setBuyerReference(String buyerReference) {
        this.buyerReference = buyerReference;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public Integer getInventoryReturned() {
        return inventoryReturned;
    }

    public void setInventoryReturned(Integer inventoryReturned) {
        this.inventoryReturned = inventoryReturned;
    }
}
