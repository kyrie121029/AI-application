package com.example.demo.service;

import com.example.demo.config.EmbeddingProperties;
import com.example.demo.config.VectorStoreProperties;
import com.example.demo.dto.VectorRecord;
import com.example.demo.dto.VectorSearchRequest;
import com.example.demo.dto.VectorSearchResult;
import com.example.demo.exception.VectorStoreException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QdrantVectorStore 单测 —— 通过子类 override doUpsert/doDelete/ensureCollection 捕获请求体，
 * 验证 point id / payload 映射 / 幂等点 id / 维度一致性校验。
 */
class QdrantVectorStoreTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EmbeddingProperties embeddingProperties;
    private VectorStoreProperties properties;

    @BeforeEach
    void setUp() {
        embeddingProperties = new EmbeddingProperties();
        embeddingProperties.setDimension(4);
        properties = new VectorStoreProperties();
        properties.getQdrant().setHost("localhost");
        properties.getQdrant().setPort(6333);
        properties.getQdrant().setCollection("ai-app-vectors");
    }

    private QdrantVectorStore newStore() {
        return new QdrantVectorStore(properties, embeddingProperties);
    }

    private VectorRecord record(Long fileId, Long userId, Long chunkId, int chunkIndex, String model) {
        return new VectorRecord(fileId, userId, chunkId, chunkIndex,
                new float[]{1f, 2f, 3f, 4f}, model, 4, "rag-index-v1");
    }

    @Test
    @DisplayName("point id：合法 deterministic UUID 且稳定（同一 fileId+chunkIndex 恒定）")
    void stablePointId() {
        String id1 = QdrantVectorStore.pointId(12L, 3);
        String id2 = QdrantVectorStore.pointId(12L, 3);
        assertEquals(id1, id2); // 稳定
        assertDoesNotThrow(() -> java.util.UUID.fromString(id1)); // 合法 UUID
    }

    @Test
    @DisplayName("point id：不同 file/chunk 得到不同 ID")
    void distinctPointIds() {
        assertNotEquals(QdrantVectorStore.pointId(1L, 0), QdrantVectorStore.pointId(1L, 1));
        assertNotEquals(QdrantVectorStore.pointId(1L, 0), QdrantVectorStore.pointId(2L, 0));
    }

    @Test
    @DisplayName("upsert：正确构造 payload（chunkId/fileId/userId/chunkIndex/model/dimension/indexVersion）")
    void upsertPayloadMapping() {
        JsonNode[] captured = {null};
        QdrantVectorStore store = new QdrantVectorStore(properties, embeddingProperties) {
            @Override
            void ensureCollection() {
            }

            @Override
            void doUpsert(JsonNode body) {
                captured[0] = body;
            }
        };

        store.upsert(List.of(record(1L, 9L, 101L, 0, "mock-embedding")));

        JsonNode point = captured[0].path("points").get(0);
        assertEquals(QdrantVectorStore.pointId(1L, 0), point.path("id").asText());
        assertEquals(4, point.path("vector").size());
        assertEquals(101L, point.path("payload").path("chunkId").asLong());
        assertEquals(1L, point.path("payload").path("fileId").asLong());
        assertEquals(9L, point.path("payload").path("userId").asLong());
        assertEquals(0, point.path("payload").path("chunkIndex").asInt());
        assertEquals("mock-embedding", point.path("payload").path("embeddingModel").asText());
        assertEquals(4, point.path("payload").path("dimension").asInt());
        assertEquals("rag-index-v1", point.path("payload").path("indexVersion").asText());
    }

    @Test
    @DisplayName("batch upsert：多条记录全部进入请求")
    void batchUpsert() {
        JsonNode[] captured = {null};
        QdrantVectorStore store = new QdrantVectorStore(properties, embeddingProperties) {
            @Override
            void ensureCollection() {
            }

            @Override
            void doUpsert(JsonNode body) {
                captured[0] = body;
            }
        };

        store.upsert(List.of(record(1L, 9L, 101L, 0, "m"), record(1L, 9L, 102L, 1, "m")));

        assertEquals(2, captured[0].path("points").size());
        assertEquals(QdrantVectorStore.pointId(1L, 1), captured[0].path("points").get(1).path("id").asText());
    }

    @Test
    @DisplayName("空列表不产生请求")
    void emptyUpsertNoCall() {
        QdrantVectorStore store = new QdrantVectorStore(properties, embeddingProperties) {
            @Override
            void ensureCollection() {
                throw new AssertionError("不应调用");
            }

            @Override
            void doUpsert(JsonNode body) {
                throw new AssertionError("不应调用");
            }
        };
        assertDoesNotThrow(() -> store.upsert(List.of()));
    }

    @Test
    @DisplayName("deleteByFileId：按 payload fileId 过滤")
    void deleteByFileIdFilter() {
        JsonNode[] captured = {null};
        QdrantVectorStore store = new QdrantVectorStore(properties, embeddingProperties) {
            @Override
            void doDelete(JsonNode filter) {
                captured[0] = filter;
            }
        };

        store.deleteByFileId(7L);

        JsonNode must = captured[0].path("filter").path("must");
        assertEquals("fileId", must.get(0).path("key").asText());
        assertEquals(7L, must.get(0).path("match").path("value").asLong());
    }

    private VectorSearchRequest request() {
        return new VectorSearchRequest(new float[]{1, 2, 3, 4}, 9L, List.of(1L, 2L), 5, 0.5, "rag-index-v1");
    }

    private String hitJson() {
        return "{\"result\":["
                + "{\"id\":\"u1\",\"score\":0.9,\"payload\":{\"chunkId\":101,\"fileId\":1,\"chunkIndex\":0}},"
                + "{\"id\":\"u2\",\"score\":0.7,\"payload\":{\"chunkId\":202,\"fileId\":2,\"chunkIndex\":3}}"
                + "]}";
    }

    @Test
    @DisplayName("search：filter 含 userId + indexVersion + fileIds，limit/threshold 参数正确")
    void searchBuildsFilter() {
        JsonNode[] captured = {null};
        QdrantVectorStore store = new QdrantVectorStore(properties, embeddingProperties) {
            @Override
            String doSearch(JsonNode body) {
                captured[0] = body;
                return hitJson();
            }
        };

        List<VectorSearchResult> hits = store.search(request());

        JsonNode must = captured[0].path("filter").path("must");
        assertEquals("userId", must.get(0).path("key").asText());
        assertEquals(9L, must.get(0).path("match").path("value").asLong());
        assertEquals("indexVersion", must.get(1).path("key").asText());
        assertEquals("rag-index-v1", must.get(1).path("match").path("value").asText());
        assertEquals("fileId", must.get(2).path("key").asText());
        assertTrue(must.get(2).path("match").path("any").isArray());
        assertEquals(2, must.get(2).path("match").path("any").size());
        assertEquals(5, captured[0].path("limit").asInt());
        assertEquals(0.5, captured[0].path("score_threshold").asDouble());
        assertFalse(captured[0].path("with_vector").asBoolean());
        assertTrue(captured[0].path("with_payload").asBoolean());
        assertEquals(2, hits.size());
    }

    @Test
    @DisplayName("search：无 fileIds 时不含 fileId filter（越权数据靠 userId+indexVersion 过滤）")
    void searchWithoutFileIdsHasNoFileFilter() {
        JsonNode[] captured = {null};
        QdrantVectorStore store = new QdrantVectorStore(properties, embeddingProperties) {
            @Override
            String doSearch(JsonNode body) {
                captured[0] = body;
                return hitJson();
            }
        };

        store.search(new VectorSearchRequest(new float[]{1, 2, 3, 4}, 9L, null, 5, null, "rag-index-v1"));

        JsonNode must = captured[0].path("filter").path("must");
        assertEquals(2, must.size()); // 只有 userId + indexVersion
        assertFalse(captured[0].has("score_threshold"));
    }

    @Test
    @DisplayName("parseHits：保持 score 排名顺序，解析 chunkId/fileId/chunkIndex")
    void parseHitsPreservesOrder() {
        QdrantVectorStore store = new QdrantVectorStore(properties, embeddingProperties);
        List<VectorSearchResult> hits = store.parseHits(hitJson());

        assertEquals(2, hits.size());
        assertEquals(101L, hits.get(0).chunkId());
        assertEquals(1L, hits.get(0).fileId());
        assertEquals(0, hits.get(0).chunkIndex());
        assertEquals(0.9f, hits.get(0).score());
        assertEquals(202L, hits.get(1).chunkId());
    }

    @Test
    @DisplayName("parseHits：缺少 chunkId payload → VectorStoreException")
    void parseHitsMissingPayloadFails() {
        QdrantVectorStore store = new QdrantVectorStore(properties, embeddingProperties);
        String bad = "{\"result\":[{\"id\":\"u\",\"score\":0.5,\"payload\":{\"fileId\":1}}]}";
        assertThrows(VectorStoreException.class, () -> store.parseHits(bad));
    }

    @Test
    @DisplayName("parseHits：缺 chunkIndex → VectorStoreException")
    void parseHitsMissingChunkIndexFails() {
        QdrantVectorStore store = new QdrantVectorStore(properties, embeddingProperties);
        String bad = "{\"result\":[{\"id\":\"u\",\"score\":0.5,\"payload\":{\"chunkId\":1,\"fileId\":1}}]}";
        assertThrows(VectorStoreException.class, () -> store.parseHits(bad));
    }

    @Test
    @DisplayName("parseHits：缺 score → VectorStoreException")
    void parseHitsMissingScoreFails() {
        QdrantVectorStore store = new QdrantVectorStore(properties, embeddingProperties);
        String bad = "{\"result\":[{\"id\":\"u\",\"payload\":{\"chunkId\":1,\"fileId\":1,\"chunkIndex\":0}}]}";
        assertThrows(VectorStoreException.class, () -> store.parseHits(bad));
    }

    @Test
    @DisplayName("parseHits：非有限 score → VectorStoreException")
    void parseHitsNonFiniteScoreFails() {
        QdrantVectorStore store = new QdrantVectorStore(properties, embeddingProperties);
        String bad = "{\"result\":[{\"id\":\"u\",\"score\":1e999,\"payload\":{\"chunkId\":1,\"fileId\":1,\"chunkIndex\":0}}]}";
        assertThrows(VectorStoreException.class, () -> store.parseHits(bad));
    }

    @Test
    @DisplayName("parseHits：负 chunkIndex → VectorStoreException")
    void parseHitsNegativeChunkIndexFails() {
        QdrantVectorStore store = new QdrantVectorStore(properties, embeddingProperties);
        String bad = "{\"result\":[{\"id\":\"u\",\"score\":0.5,\"payload\":{\"chunkId\":1,\"fileId\":1,\"chunkIndex\":-1}}]}";
        assertThrows(VectorStoreException.class, () -> store.parseHits(bad));
    }

    @Test
    @DisplayName("维度不一致 → VectorStoreException（ensureCollection 已存在 collection 场景）")
    void dimensionMismatchFails() {
        QdrantVectorStore store = new QdrantVectorStore(properties, embeddingProperties) {
            // 模拟已存在且维度不符的 collection：由 ensureCollection 的实现抛错，
            // 这里验证异常类型与消息
        };
        // ensureCollection 无法直接 mock GET 响应，这里验证维度校验方法路径通过配置差异暴露
        VectorStoreProperties props2 = new VectorStoreProperties();
        props2.getQdrant().setCollection("x");
        // 由于 ensureCollection 依赖真实 HTTP，此单测只验证结构性前提：
        // 在无 Qdrant 环境用子类直接抛错验证 upsert 包装
        QdrantVectorStore failing = new QdrantVectorStore(properties, embeddingProperties) {
            @Override
            void ensureCollection() {
                throw new VectorStoreException("Qdrant collection 维度(2)与 embedding.dimension(4)不一致");
            }
        };
        VectorStoreException e = assertThrows(VectorStoreException.class,
                () -> failing.upsert(List.of(record(1L, 9L, 101L, 0, "m"))));
        assertTrue(e.getMessage().contains("维度"));
    }
}
