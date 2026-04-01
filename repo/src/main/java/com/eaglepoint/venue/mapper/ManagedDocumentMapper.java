package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.ManagedDocument;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ManagedDocumentMapper {
    int insert(ManagedDocument managedDocument);

    ManagedDocument findById(@Param("id") Long id);

    List<ManagedDocument> findAll();
}
