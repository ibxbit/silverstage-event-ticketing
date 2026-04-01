package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.AuthSession;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface AuthSessionMapper {
    int insert(AuthSession session);
    AuthSession findValidByToken(@Param("token") String token, @Param("now") LocalDateTime now);
}
