package com.eaglepoint.venue.api.dto;

import java.util.ArrayList;
import java.util.List;

public class PagedDiscoveryResponse {
    private Integer page;
    private Integer size;
    private Long total;
    private List<DiscoveryResultItem> items = new ArrayList<DiscoveryResultItem>();

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public List<DiscoveryResultItem> getItems() {
        return items;
    }

    public void setItems(List<DiscoveryResultItem> items) {
        this.items = items;
    }
}
