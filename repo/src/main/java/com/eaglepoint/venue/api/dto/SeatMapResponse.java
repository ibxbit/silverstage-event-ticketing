package com.eaglepoint.venue.api.dto;

import java.util.ArrayList;
import java.util.List;

public class SeatMapResponse {
    private Long sessionId;
    private String sessionTitle;
    private Integer remainingQuota;
    private Boolean lowInventory;
    private Boolean quotaReached;
    private List<ZoneSeatMap> zones = new ArrayList<ZoneSeatMap>();

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionTitle() {
        return sessionTitle;
    }

    public void setSessionTitle(String sessionTitle) {
        this.sessionTitle = sessionTitle;
    }

    public Integer getRemainingQuota() {
        return remainingQuota;
    }

    public void setRemainingQuota(Integer remainingQuota) {
        this.remainingQuota = remainingQuota;
    }

    public Boolean getLowInventory() {
        return lowInventory;
    }

    public void setLowInventory(Boolean lowInventory) {
        this.lowInventory = lowInventory;
    }

    public Boolean getQuotaReached() {
        return quotaReached;
    }

    public void setQuotaReached(Boolean quotaReached) {
        this.quotaReached = quotaReached;
    }

    public List<ZoneSeatMap> getZones() {
        return zones;
    }

    public void setZones(List<ZoneSeatMap> zones) {
        this.zones = zones;
    }

    public static class ZoneSeatMap {
        private Long zoneId;
        private String zoneCode;
        private String zoneName;
        private List<SeatItem> seats = new ArrayList<SeatItem>();

        public Long getZoneId() {
            return zoneId;
        }

        public void setZoneId(Long zoneId) {
            this.zoneId = zoneId;
        }

        public String getZoneCode() {
            return zoneCode;
        }

        public void setZoneCode(String zoneCode) {
            this.zoneCode = zoneCode;
        }

        public String getZoneName() {
            return zoneName;
        }

        public void setZoneName(String zoneName) {
            this.zoneName = zoneName;
        }

        public List<SeatItem> getSeats() {
            return seats;
        }

        public void setSeats(List<SeatItem> seats) {
            this.seats = seats;
        }
    }

    public static class SeatItem {
        private Long seatId;
        private String seatNumber;
        private String status;

        public Long getSeatId() {
            return seatId;
        }

        public void setSeatId(Long seatId) {
            this.seatId = seatId;
        }

        public String getSeatNumber() {
            return seatNumber;
        }

        public void setSeatNumber(String seatNumber) {
            this.seatNumber = seatNumber;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
