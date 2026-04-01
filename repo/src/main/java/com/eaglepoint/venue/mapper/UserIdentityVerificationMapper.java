package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.UserIdentityVerification;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserIdentityVerificationMapper {
    int insert(UserIdentityVerification row);
    UserIdentityVerification findById(@Param("id") Long id);
    UserIdentityVerification findLatestByUserId(@Param("userId") Long userId);
    List<UserIdentityVerification> findPending();
    int review(@Param("id") Long id, @Param("status") String status, @Param("reviewedBy") String reviewedBy, @Param("reviewNotes") String reviewNotes);
}
