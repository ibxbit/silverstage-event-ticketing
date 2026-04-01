package com.eaglepoint.venue.api.dto;

import java.util.ArrayList;
import java.util.List;

public class PagedDocumentsResponse {
    private Integer page;
    private Integer size;
    private Long total;
    private List<DocumentListItem> items = new ArrayList<DocumentListItem>();

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

    public List<DocumentListItem> getItems() {
        return items;
    }

    public void setItems(List<DocumentListItem> items) {
        this.items = items;
    }
}
