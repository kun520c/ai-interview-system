package com.kun.aiinterview.knowledge.mapper;

import com.kun.aiinterview.knowledge.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface KnowledgeDocumentMapper {

    int insertDocument(KnowledgeDocument document);

    KnowledgeDocument selectById(@Param("id") Long id);

    int claimProcessing(@Param("id") Long id);

    int reclaimStaleProcessing(
            @Param("id") Long id,
            @Param("cutoff") LocalDateTime cutoff
    );

    int claimFailedProcessing(@Param("id") Long id);

    int markReady(@Param("id") Long id);

    int markFailed(
            @Param("id") Long id,
            @Param("errorMessage") String errorMessage
    );
}
