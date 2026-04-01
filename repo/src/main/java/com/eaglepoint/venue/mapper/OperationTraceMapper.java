package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.OperationTrace;

import java.util.List;

public interface OperationTraceMapper {
    int insert(OperationTrace row);
    List<OperationTrace> findAll();
}
