package com.kun.aiinterview.knowledge.mapper;

import com.kun.aiinterview.knowledge.entity.RagRetrievalBatch;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RagRetrievalBatchMapper {

    int insertBatch(
            RagRetrievalBatch batch
    );

    RagRetrievalBatch getByBatchId(
            String retrievalBatchId
    );
}
