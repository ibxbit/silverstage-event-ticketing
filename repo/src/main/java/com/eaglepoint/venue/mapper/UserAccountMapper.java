package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.UserAccount;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface UserAccountMapper {
    int insert(UserAccount userAccount);
    UserAccount findByUsername(@Param("username") String username);
    UserAccount findById(@Param("id") Long id);
    int updateLoginFailure(@Param("id") Long id, @Param("failedAttempts") Integer failedAttempts, @Param("lockoutUntil") LocalDateTime lockoutUntil);
    int resetLoginFailures(@Param("id") Long id);
}
