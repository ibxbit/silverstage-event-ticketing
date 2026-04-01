package com.eaglepoint.venue.api.dto;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CreateTicketTypeRequest {
    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal basePrice;

    @NotBlank
    private String visibilityScope;

    @NotNull
    private LocalDateTime saleStart;

    @NotNull
    private LocalDateTime saleEnd;

    @NotNull
    @Min(1)
    private Integer totalInventory;

    @Min(0)
    @Max(100)
    private Integer onlineQuotaPercent;

    @Min(0)
    @Max(100)
    private Integer boxOfficeQuotaPercent;

    @Valid
    @NotEmpty
    private List<TierRuleRequest> tierRules;

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

    public Integer getTotalInventory() {
        return totalInventory;
    }

    public void setTotalInventory(Integer totalInventory) {
        this.totalInventory = totalInventory;
    }

    public Integer getOnlineQuotaPercent() {
        return onlineQuotaPercent;
    }

    public void setOnlineQuotaPercent(Integer onlineQuotaPercent) {
        this.onlineQuotaPercent = onlineQuotaPercent;
    }

    public Integer getBoxOfficeQuotaPercent() {
        return boxOfficeQuotaPercent;
    }

    public void setBoxOfficeQuotaPercent(Integer boxOfficeQuotaPercent) {
        this.boxOfficeQuotaPercent = boxOfficeQuotaPercent;
    }

    public List<TierRuleRequest> getTierRules() {
        return tierRules;
    }

    public void setTierRules(List<TierRuleRequest> tierRules) {
        this.tierRules = tierRules;
    }

    public static class TierRuleRequest {
        @NotNull
        @Min(1)
        private Integer minQuantity;

        @NotNull
        @DecimalMin("0.0")
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
}
