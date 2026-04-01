package com.eaglepoint.venue.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TicketTypeResponse {
    private Long id;
    private String code;
    private String name;
    private BigDecimal basePrice;
    private String visibilityScope;
    private LocalDateTime saleStart;
    private LocalDateTime saleEnd;
    private String saleWindowLabel;
    private Integer totalInventory;
    private List<TierRule> tierRules = new ArrayList<TierRule>();
    private List<ChannelQuota> channelQuotas = new ArrayList<ChannelQuota>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public String getVisibilityScope() {
        return visibilityScope;
    }

    public void setVisibilityScope(String visibilityScope) {
        this.visibilityScope = visibilityScope;
    }

    public LocalDateTime getSaleStart() {
        return saleStart;
    }

    public void setSaleStart(LocalDateTime saleStart) {
        this.saleStart = saleStart;
    }

    public LocalDateTime getSaleEnd() {
        return saleEnd;
    }

    public void setSaleEnd(LocalDateTime saleEnd) {
        this.saleEnd = saleEnd;
    }

    public String getSaleWindowLabel() {
        return saleWindowLabel;
    }

    public void setSaleWindowLabel(String saleWindowLabel) {
        this.saleWindowLabel = saleWindowLabel;
    }

    public Integer getTotalInventory() {
        return totalInventory;
    }

    public void setTotalInventory(Integer totalInventory) {
        this.totalInventory = totalInventory;
    }

    public List<TierRule> getTierRules() {
        return tierRules;
    }

    public void setTierRules(List<TierRule> tierRules) {
        this.tierRules = tierRules;
    }

    public List<ChannelQuota> getChannelQuotas() {
        return channelQuotas;
    }

    public void setChannelQuotas(List<ChannelQuota> channelQuotas) {
        this.channelQuotas = channelQuotas;
    }

    public static class TierRule {
        private Integer minQuantity;
        private BigDecimal price;

        public Integer getMinQuantity() {
            return minQuantity;
        }

        public void setMinQuantity(Integer minQuantity) {
            this.minQuantity = minQuantity;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }
    }

    public static class ChannelQuota {
        private String channel;
        private Integer allocated;
        private Integer sold;

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }

        public Integer getAllocated() {
            return allocated;
        }

        public void setAllocated(Integer allocated) {
            this.allocated = allocated;
        }

        public Integer getSold() {
            return sold;
        }

        public void setSold(Integer sold) {
            this.sold = sold;
        }
    }
}
