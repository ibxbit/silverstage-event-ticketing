package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.ManagedDocumentVersion;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ManagedDocumentVersionMapper {
    int insert(ManagedDocumentVersion managedDocumentVersion);

    ManagedDocumentVersion findLatestByDocumentId(@Param("documentId") Long documentId);

    ManagedDocumentVersion findById(@Param("id") Long id);

    List<ManagedDocumentVersion> findByDocumentId(@Param("documentId") Long documentId);
}
