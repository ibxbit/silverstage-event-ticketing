package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.ReconciliationException;

import java.util.List;

public interface ReconciliationExceptionMapper {
    int insert(ReconciliationException row);
    List<ReconciliationException> findAll();
}
