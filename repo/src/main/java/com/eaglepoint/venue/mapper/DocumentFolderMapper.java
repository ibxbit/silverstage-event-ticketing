package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.DocumentFolder;
import org.apache.ibatis.annotations.Param;

public interface DocumentFolderMapper {
    DocumentFolder findByPath(@Param("folderPath") String folderPath);

    DocumentFolder findById(@Param("id") Long id);

    int insert(DocumentFolder folder);
}
