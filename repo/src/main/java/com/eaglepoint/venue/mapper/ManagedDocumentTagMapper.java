package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.ManagedDocumentTag;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ManagedDocumentTagMapper {
    int insert(ManagedDocumentTag tag);

    int deleteByDocumentId(@Param("documentId") Long documentId);

    List<String> findTagsByDocumentId(@Param("documentId") Long documentId);
}
