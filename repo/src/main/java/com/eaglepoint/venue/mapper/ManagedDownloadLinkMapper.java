package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.ManagedDownloadLink;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface ManagedDownloadLinkMapper {
    int insert(ManagedDownloadLink link);

    ManagedDownloadLink findValidByToken(@Param("token") String token, @Param("now") LocalDateTime now);
}
