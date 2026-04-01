package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.RevenueShareRecord;

import java.util.List;

public interface RevenueShareRecordMapper {
    int insert(RevenueShareRecord row);
    List<RevenueShareRecord> findAll();
}
