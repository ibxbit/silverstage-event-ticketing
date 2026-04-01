package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.UserNotification;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserNotificationMapper {
    int insert(UserNotification notification);

    UserNotification findById(@Param("id") Long id);

    List<UserNotification> findByUsername(@Param("username") String username);

    int markAsRead(@Param("id") Long id);

    int markAsReadByUsername(@Param("id") Long id, @Param("username") String username);
}
