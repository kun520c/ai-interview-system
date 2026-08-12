package com.kun.aiinterview.knowledge.mapper;

import com.kun.aiinterview.knowledge.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeChunkMapper {

    int batchInsert(@Param("chunks") List<KnowledgeChunk> chunks);

    int deleteByDocumentIdAndVersion(
            @Param("documentId") Long documentId,

            @Param("documentVersion") Integer documentVersion
                                     );
}
