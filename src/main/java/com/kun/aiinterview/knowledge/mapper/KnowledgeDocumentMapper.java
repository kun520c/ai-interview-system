package com.kun.aiinterview.knowledge.mapper;

import com.kun.aiinterview.knowledge.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface KnowledgeDocumentMapper {

    int insertDocument(KnowledgeDocument document);

    KnowledgeDocument selectById(@Param("id") Long id);

    KnowledgeDocument lockById(@Param("id") Long id);

    int claimProcessing(@Param("id") Long id, @Param("attemptId") String attemptId);

    int reclaimStaleProcessing(
            @Param("id") Long id,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("attemptId") String attemptId
    );

    int claimFailedProcessing(@Param("id") Long id, @Param("attemptId") String attemptId);

    int markReady(@Param("id") Long id, @Param("attemptId") String attemptId);

    int markFailed(
            @Param("id") Long id,
            @Param("errorMessage") String errorMessage,
            @Param("attemptId") String attemptId
    );
}
