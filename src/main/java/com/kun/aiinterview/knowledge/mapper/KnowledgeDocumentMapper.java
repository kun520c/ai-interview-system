package com.kun.aiinterview.knowledge.mapper;

import com.kun.aiinterview.knowledge.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeDocumentMapper {

    int insertDocument(KnowledgeDocument document);

    KnowledgeDocument selectById(Long id);

    int claimProcessing(Long id);

    int markReady(Long id);

    int markFailed(
            @Param("id") Long id,
            @Param("errorMessage") String errorMessage
    );

}
