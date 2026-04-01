package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.UserPenalty;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserPenaltyMapper {
    int insert(UserPenalty penalty);

    List<UserPenalty> findActiveByUsername(@Param("username") String username, @Param("now") LocalDateTime now);

    int deactivateExpired(@Param("now") LocalDateTime now);
}
