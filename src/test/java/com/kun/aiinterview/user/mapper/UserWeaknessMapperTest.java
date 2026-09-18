package com.kun.aiinterview.user.mapper;

import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.user.entity.UserWeakness;
import com.kun.aiinterview.user.enums.UserWeaknessStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class UserWeaknessMapperTest {

    private static final String KNOWLEDGE_POINT = "HashMap";

    @Autowired
    private UserWeaknessMapper userWeaknessMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldInsertActiveWeaknessOnFirstDiscovery() {
        long userId = insertUser();

        int affectedRows = userWeaknessMapper.upsertDiscoveredWeakness(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                KNOWLEDGE_POINT,
                new BigDecimal("40.00")
        );

        UserWeakness found = userWeaknessMapper
                .getByUserCategoryAndKnowledgePoint(
                        userId,
                        QuestionCategory.JAVA_COLLECTION,
                        KNOWLEDGE_POINT
                );

        assertNotNull(found);
        assertAll(
                () -> assertTrue(affectedRows >= 1),
                () -> assertNotNull(found.getId()),
                () -> assertEquals(userId, found.getUserId()),
                () -> assertEquals(
                        QuestionCategory.JAVA_COLLECTION,
                        found.getCategory()
                ),
                () -> assertEquals(KNOWLEDGE_POINT, found.getKnowledgePoint()),
                () -> assertEquals(
                        0,
                        new BigDecimal("40.00")
                                .compareTo(found.getWeaknessScore())
                ),
                () -> assertEquals(1, found.getDiscoveredCount()),
                () -> assertEquals(UserWeaknessStatus.ACTIVE, found.getStatus()),
                () -> assertNotNull(found.getLastDiscoveredAt()),
                () -> assertNull(found.getResolvedAt()),
                () -> assertNotNull(found.getCreatedAt()),
                () -> assertNotNull(found.getUpdatedAt())
        );
    }

    @Test
    void shouldIncrementCountAndUpdateScoreWhenActiveIsDiscoveredAgain() {
        long userId = insertUser();
        userWeaknessMapper.upsertDiscoveredWeakness(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                KNOWLEDGE_POINT,
                new BigDecimal("40.00")
        );
        UserWeakness first = userWeaknessMapper
                .getByUserCategoryAndKnowledgePoint(
                        userId,
                        QuestionCategory.JAVA_COLLECTION,
                        KNOWLEDGE_POINT
                );

        int affectedRows = userWeaknessMapper.upsertDiscoveredWeakness(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                KNOWLEDGE_POINT,
                new BigDecimal("35.00")
        );
        UserWeakness second = userWeaknessMapper
                .getByUserCategoryAndKnowledgePoint(
                        userId,
                        QuestionCategory.JAVA_COLLECTION,
                        KNOWLEDGE_POINT
                );

        assertAll(
                () -> assertTrue(affectedRows >= 1),
                () -> assertEquals(first.getId(), second.getId()),
                () -> assertEquals(2, second.getDiscoveredCount()),
                () -> assertEquals(
                        0,
                        new BigDecimal("35.00")
                                .compareTo(second.getWeaknessScore())
                ),
                () -> assertEquals(UserWeaknessStatus.ACTIVE, second.getStatus()),
                () -> assertNull(second.getResolvedAt()),
                () -> assertTrue(
                        !second.getLastDiscoveredAt()
                                .isBefore(first.getLastDiscoveredAt())
                )
        );
    }

    @Test
    void shouldReactivateResolvedWeaknessOnRediscovery() {
        long userId = insertUser();
        userWeaknessMapper.upsertDiscoveredWeakness(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                KNOWLEDGE_POINT,
                new BigDecimal("40.00")
        );
        userWeaknessMapper.resolveActiveWeakness(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                KNOWLEDGE_POINT,
                new BigDecimal("20.00")
        );
        UserWeakness resolved = userWeaknessMapper
                .getByUserCategoryAndKnowledgePoint(
                        userId,
                        QuestionCategory.JAVA_COLLECTION,
                        KNOWLEDGE_POINT
                );
        assertEquals(UserWeaknessStatus.RESOLVED, resolved.getStatus());
        assertNotNull(resolved.getResolvedAt());

        int affectedRows = userWeaknessMapper.upsertDiscoveredWeakness(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                KNOWLEDGE_POINT,
                new BigDecimal("45.00")
        );
        UserWeakness reactivated = userWeaknessMapper
                .getByUserCategoryAndKnowledgePoint(
                        userId,
                        QuestionCategory.JAVA_COLLECTION,
                        KNOWLEDGE_POINT
                );

        assertAll(
                () -> assertTrue(affectedRows >= 1),
                () -> assertEquals(resolved.getId(), reactivated.getId()),
                () -> assertEquals(UserWeaknessStatus.ACTIVE, reactivated.getStatus()),
                () -> assertEquals(2, reactivated.getDiscoveredCount()),
                () -> assertEquals(
                        0,
                        new BigDecimal("45.00")
                                .compareTo(reactivated.getWeaknessScore())
                ),
                () -> assertNull(reactivated.getResolvedAt())
        );
    }

    @Test
    void shouldResolveActiveWeaknessWithoutChangingCountOrLastDiscoveredAt() {
        long userId = insertUser();
        userWeaknessMapper.upsertDiscoveredWeakness(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                KNOWLEDGE_POINT,
                new BigDecimal("40.00")
        );
        UserWeakness active = userWeaknessMapper
                .getByUserCategoryAndKnowledgePoint(
                        userId,
                        QuestionCategory.JAVA_COLLECTION,
                        KNOWLEDGE_POINT
                );
        LocalDateTime lastDiscoveredAt = active.getLastDiscoveredAt();

        int affectedRows = userWeaknessMapper.resolveActiveWeakness(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                KNOWLEDGE_POINT,
                new BigDecimal("20.00")
        );
        UserWeakness resolved = userWeaknessMapper
                .getByUserCategoryAndKnowledgePoint(
                        userId,
                        QuestionCategory.JAVA_COLLECTION,
                        KNOWLEDGE_POINT
                );

        assertAll(
                () -> assertEquals(1, affectedRows),
                () -> assertEquals(UserWeaknessStatus.RESOLVED, resolved.getStatus()),
                () -> assertEquals(1, resolved.getDiscoveredCount()),
                () -> assertEquals(lastDiscoveredAt, resolved.getLastDiscoveredAt()),
                () -> assertNotNull(resolved.getResolvedAt()),
                () -> assertEquals(
                        0,
                        new BigDecimal("20.00")
                                .compareTo(resolved.getWeaknessScore())
                )
        );
    }

    @Test
    void shouldNotCreateRowWhenResolvingMissingWeakness() {
        long userId = insertUser();

        int affectedRows = userWeaknessMapper.resolveActiveWeakness(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                KNOWLEDGE_POINT,
                new BigDecimal("20.00")
        );

        assertEquals(0, affectedRows);
        assertNull(userWeaknessMapper.getByUserCategoryAndKnowledgePoint(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                KNOWLEDGE_POINT
        ));
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM user_weakness
                WHERE user_id = ?
                """,
                Integer.class,
                userId
        );
        assertEquals(0, count);
    }

    @Test
    void shouldNotRefreshResolvedAtWhenResolvingAlreadyResolvedWeakness() {
        long userId = insertUser();
        userWeaknessMapper.upsertDiscoveredWeakness(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                KNOWLEDGE_POINT,
                new BigDecimal("40.00")
        );
        userWeaknessMapper.resolveActiveWeakness(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                KNOWLEDGE_POINT,
                new BigDecimal("20.00")
        );
        UserWeakness resolved = userWeaknessMapper
                .getByUserCategoryAndKnowledgePoint(
                        userId,
                        QuestionCategory.JAVA_COLLECTION,
                        KNOWLEDGE_POINT
                );
        LocalDateTime originalResolvedAt = resolved.getResolvedAt();
        BigDecimal originalScore = resolved.getWeaknessScore();

        int affectedRows = userWeaknessMapper.resolveActiveWeakness(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                KNOWLEDGE_POINT,
                new BigDecimal("10.00")
        );
        UserWeakness unchanged = userWeaknessMapper
                .getByUserCategoryAndKnowledgePoint(
                        userId,
                        QuestionCategory.JAVA_COLLECTION,
                        KNOWLEDGE_POINT
                );

        assertAll(
                () -> assertEquals(0, affectedRows),
                () -> assertEquals(UserWeaknessStatus.RESOLVED, unchanged.getStatus()),
                () -> assertEquals(originalResolvedAt, unchanged.getResolvedAt()),
                () -> assertEquals(
                        0,
                        originalScore.compareTo(unchanged.getWeaknessScore())
                ),
                () -> assertEquals(1, unchanged.getDiscoveredCount())
        );
    }

    @Test
    void shouldRejectDuplicateUserCategoryAndKnowledgePoint() {
        long userId = insertUser();
        userWeaknessMapper.upsertDiscoveredWeakness(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                KNOWLEDGE_POINT,
                new BigDecimal("40.00")
        );

        assertThrows(
                DuplicateKeyException.class,
                () -> jdbcTemplate.update(
                        """
                        INSERT INTO user_weakness
                            (user_id, category, knowledge_point, weakness_score,
                             discovered_count, status, resolved_at)
                        VALUES (?, 'JAVA_COLLECTION', ?, 30.00, 1, 'ACTIVE', NULL)
                        """,
                        userId,
                        KNOWLEDGE_POINT
                )
        );
    }

    @Test
    void shouldTreatCategoryAndKnowledgePointTogetherAsUniqueKey() {
        long userId = insertUser();
        userWeaknessMapper.upsertDiscoveredWeakness(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                KNOWLEDGE_POINT,
                new BigDecimal("40.00")
        );
        userWeaknessMapper.upsertDiscoveredWeakness(
                userId,
                QuestionCategory.JAVA_CONCURRENCY,
                KNOWLEDGE_POINT,
                new BigDecimal("50.00")
        );
        userWeaknessMapper.upsertDiscoveredWeakness(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                "ConcurrentHashMap",
                new BigDecimal("45.00")
        );

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_weakness WHERE user_id = ?",
                Integer.class,
                userId
        );
        assertEquals(3, rowCount);
        assertEquals(
                1,
                userWeaknessMapper.getByUserCategoryAndKnowledgePoint(
                        userId,
                        QuestionCategory.JAVA_COLLECTION,
                        KNOWLEDGE_POINT
                ).getDiscoveredCount()
        );
        assertEquals(
                QuestionCategory.JAVA_CONCURRENCY,
                userWeaknessMapper.getByUserCategoryAndKnowledgePoint(
                        userId,
                        QuestionCategory.JAVA_CONCURRENCY,
                        KNOWLEDGE_POINT
                ).getCategory()
        );
    }

    private long insertUser() {
        String uniqueValue = UUID.randomUUID()
                .toString()
                .replace("-", "");
        String account = "weakness-mapper-" + uniqueValue;
        jdbcTemplate.update(
                """
                INSERT INTO `user`
                    (account, username, password, email, role, status)
                VALUES (?, ?, ?, ?, 'USER', 'ENABLED')
                """,
                account,
                "Weakness Mapper测试用户",
                "test-password-hash",
                account + "@example.com"
        );
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE account = ?",
                Long.class,
                account
        );
        assertNotNull(id);
        return id;
    }
}
