package com.kun.aiinterview.knowledge.mapper;

import com.kun.aiinterview.knowledge.entity.RagHitLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RagHitLogMapper {

    int batchInsert(
            @Param("hits")
            List<RagHitLog> hits
    );

    List<RagHitLog> listByBatchId(
            String retrievalBatchId
    );
}
