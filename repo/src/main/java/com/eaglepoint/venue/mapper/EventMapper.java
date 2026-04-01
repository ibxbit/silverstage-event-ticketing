package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.Event;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface EventMapper {
    List<Event> findAll();

    Event findById(@Param("id") Long id);

    int insert(Event event);
}
