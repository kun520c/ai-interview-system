package com.kun.aiinterview.user.mapper;

import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.user.entity.UserWeakness;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface UserWeaknessMapper {

    UserWeakness getByUserCategoryAndKnowledgePoint(
            @Param("userId") Long userId,
            @Param("category") QuestionCategory category,
            @Param("knowledgePoint") String knowledgePoint
    );

    int upsertDiscoveredWeakness(
            @Param("userId") Long userId,
            @Param("category") QuestionCategory category,
            @Param("knowledgePoint") String knowledgePoint,
            @Param("weaknessScore") BigDecimal weaknessScore
    );

    int resolveActiveWeakness(
            @Param("userId") Long userId,
            @Param("category") QuestionCategory category,
            @Param("knowledgePoint") String knowledgePoint,
            @Param("weaknessScore") BigDecimal weaknessScore
    );
}
