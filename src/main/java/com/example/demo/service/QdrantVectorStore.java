package com.example.demo.service;

import com.example.demo.config.EmbeddingProperties;
import com.example.demo.config.VectorStoreProperties;
import com.example.demo.dto.VectorRecord;
import com.example.demo.dto.VectorSearchRequest;
import com.example.demo.dto.VectorSearchResult;
import com.example.demo.exception.VectorStoreException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Qdrant 向量存储实现（REST API）—— 仅在 vector-store.type=qdrant 时生效。
 * <p>
 * 设计：
 *   - 一个 DocumentChunk 对应一个 Point，point id = 基于 fileId+chunkIndex 的确定性 UUID（稳定、幂等 upsert）；
 *   - payload 保存 chunkId/fileId/userId/chunkIndex/embeddingModel/dimension/indexVersion；
 *   - 首次 upsert 前 ensureCollection：不存在则按 embedding.dimension 创建（Cosine），
 *     存在但向量维度不一致则抛 VectorStoreException（Fail Fast）；
 *   - deleteByFileId 按 payload fileId 过滤删除。
 */
@Service
@ConditionalOnProperty(name = "vector-store.type", havingValue = "qdrant", matchIfMissing = true)
public class QdrantVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStore.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VectorStoreProperties properties;
    private final EmbeddingProperties embeddingProperties;
    private final String baseUrl;

    public QdrantVectorStore(VectorStoreProperties properties, EmbeddingProperties embeddingProperties) {
        this.properties = properties;
        this.embeddingProperties = embeddingProperties;
        this.baseUrl = "http://" + properties.getQdrant().getHost() + ":" + properties.getQdrant().getPort();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public void upsert(List<VectorRecord> records) {
        if (records == null || records.isEmpty()) return;
        ensureCollection();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode points = body.putArray("points");
            for (VectorRecord r : records) {
                ObjectNode point = points.addObject();
                point.put("id", pointId(r.fileId(), r.chunkIndex()));
                ArrayNode vector = point.putArray("vector");
                for (float f : r.vector()) vector.add(f);
                ObjectNode payload = point.putObject("payload");
                payload.put("chunkId", r.chunkId());
                payload.put("fileId", r.fileId());
                payload.put("userId", r.userId());
                payload.put("chunkIndex", r.chunkIndex());
                payload.put("embeddingModel", r.model());
                payload.put("dimension", r.dimension());
                payload.put("indexVersion", r.indexVersion());
            }
            doUpsert(body);
        } catch (VectorStoreException e) {
            throw e;
        } catch (Exception e) {
            throw new VectorStoreException("Qdrant upsert 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteByFileId(Long fileId) {
        try {
            // 请求体结构：{"filter": {"must": [{"key":"fileId","match":{"value":<fileId>}}]}}
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode filterObj = body.putObject("filter");
            ArrayNode must = filterObj.putArray("must");
            must.addObject().put("key", "fileId").putObject("match").put("value", fileId);
            doDelete(body);
        } catch (Exception e) {
            throw new VectorStoreException("Qdrant deleteByFileId 失败: fileId=" + fileId, e);
        }
    }

    // ==================== HTTP（package-private，测试子类 override） ====================

    /** 确保集合存在且维度一致（不存在 → 按 embedding.dimension 创建） */
    void ensureCollection() {
        String collection = properties.getQdrant().getCollection();
        int dimension = embeddingProperties.getDimension();
        try {
            restClient.get().uri(baseUrl + "/collections/{name}", collection)
                    .retrieve()
                    .body(String.class);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                ObjectNode create = objectMapper.createObjectNode();
                create.putObject("vectors").put("size", dimension).put("distance", "Cosine");
                try {
                    restClient.put().uri(baseUrl + "/collections/{name}", collection)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(create.toString())
                            .retrieve()
                            .body(String.class);
                    log.info("Qdrant collection 已创建: {}, dimension={}", collection, dimension);
                } catch (Exception ex) {
                    throw new VectorStoreException("Qdrant 创建 collection 失败", ex);
                }
                return;
            }
            throw new VectorStoreException("Qdrant 读取 collection 失败: " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new VectorStoreException("无法连接 Qdrant: " + baseUrl, e);
        } catch (Exception e) {
            throw new VectorStoreException("Qdrant 读取 collection 失败", e);
        }

        // 已存在：校验维度
        try {
            String body = restClient.get().uri(baseUrl + "/collections/{name}", collection)
                    .retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            int existing = root.path("result").path("config").path("params").path("vectors").path("size").asInt();
            if (existing != dimension) {
                throw new VectorStoreException("Qdrant collection 维度(" + existing
                        + ")与 embedding.dimension(" + dimension + ")不一致");
            }
        } catch (VectorStoreException e) {
            throw e;
        } catch (Exception e) {
            throw new VectorStoreException("Qdrant 校验 collection 维度失败", e);
        }
    }

    /** PUT /collections/{name}/points?wait=true */
    void doUpsert(JsonNode body) {
        try {
            restClient.put().uri(baseUrl + "/collections/{name}/points?wait=true",
                            properties.getQdrant().getCollection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
        } catch (HttpStatusCodeException e) {
            throw new VectorStoreException("Qdrant upsert HTTP " + e.getStatusCode().value()
                    + ": " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            throw new VectorStoreException("Qdrant upsert 网络错误", e);
        }
    }

    /** POST /collections/{name}/points/delete?wait=true */
    void doDelete(JsonNode filter) {
        try {
            restClient.post().uri(baseUrl + "/collections/{name}/points/delete?wait=true",
                            properties.getQdrant().getCollection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(filter.toString())
                    .retrieve()
                    .body(String.class);
        } catch (HttpStatusCodeException e) {
            throw new VectorStoreException("Qdrant delete HTTP " + e.getStatusCode().value()
                    + ": " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            throw new VectorStoreException("Qdrant delete 网络错误", e);
        }
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            // query vector
            ArrayNode vector = body.putArray("vector");
            for (float f : request.queryVector()) vector.add(f);
            body.put("limit", request.topK());
            body.put("with_payload", true);
            body.put("with_vector", false);
            if (request.scoreThreshold() != null && request.scoreThreshold() > 0) {
                body.put("score_threshold", request.scoreThreshold());
            }
            // filter：must = [userId match, indexVersion match, (可选) fileId any]
            ObjectNode filterObj = body.putObject("filter");
            ArrayNode must = filterObj.putArray("must");
            must.addObject().put("key", "userId").putObject("match").put("value", request.userId());
            must.addObject().put("key", "indexVersion").putObject("match").put("value", request.indexVersion());
            if (request.fileIds() != null && !request.fileIds().isEmpty()) {
                ArrayNode any = must.addObject().put("key", "fileId").putObject("match").putArray("any");
                for (Long id : request.fileIds()) any.add(id);
            }

            String response = doSearch(body);
            return parseHits(response);
        } catch (VectorStoreException e) {
            throw e;
        } catch (Exception e) {
            throw new VectorStoreException("Qdrant search 失败: " + e.getMessage(), e);
        }
    }

    /** POST /collections/{name}/points/search */
    String doSearch(JsonNode body) {
        try {
            return restClient.post().uri(baseUrl + "/collections/{name}/points/search",
                            properties.getQdrant().getCollection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
        } catch (HttpStatusCodeException e) {
            throw new VectorStoreException("Qdrant search HTTP " + e.getStatusCode().value()
                    + ": " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            throw new VectorStoreException("Qdrant search 网络错误", e);
        }
    }

    /** 解析命中，保持 Qdrant 返回的 score 排名顺序；payload 必须含 chunkId/fileId/chunkIndex */
    List<VectorSearchResult> parseHits(String response) {
        JsonNode root;
        try {
            root = objectMapper.readTree(response);
        } catch (Exception e) {
            throw new VectorStoreException("Qdrant search 响应不是合法 JSON", e);
        }
        JsonNode hits = root.path("result");
        if (!hits.isArray()) {
            throw new VectorStoreException("Qdrant search 响应缺少 result");
        }
        List<VectorSearchResult> results = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode payload = hit.get("payload");
            if (payload == null || !payload.isObject()) {
                throw new VectorStoreException("Qdrant search 命中缺少 payload");
            }
            JsonNode chunkIdNode = payload.get("chunkId");
            if (chunkIdNode == null || !chunkIdNode.isIntegralNumber()) {
                throw new VectorStoreException("Qdrant search 命中 chunkId 缺失或非整数");
            }
            JsonNode fileIdNode = payload.get("fileId");
            if (fileIdNode == null || !fileIdNode.isIntegralNumber()) {
                throw new VectorStoreException("Qdrant search 命中 fileId 缺失或非整数");
            }
            JsonNode chunkIndexNode = payload.get("chunkIndex");
            if (chunkIndexNode == null || !chunkIndexNode.isIntegralNumber()
                    || chunkIndexNode.asInt() < 0) {
                throw new VectorStoreException("Qdrant search 命中 chunkIndex 缺失、非整数或为负");
            }
            JsonNode scoreNode = hit.get("score");
            if (scoreNode == null || !scoreNode.isNumber()) {
                throw new VectorStoreException("Qdrant search 命中缺少 score");
            }
            double score = scoreNode.asDouble();
            if (!Double.isFinite(score)) {
                throw new VectorStoreException("Qdrant search 命中 score 非有限数值");
            }
            results.add(new VectorSearchResult(
                    chunkIdNode.asLong(), fileIdNode.asLong(), chunkIndexNode.asInt(), (float) score));
        }
        return results;
    }

    /**
     * 稳定 point id：基于 "file-{fileId}-c{chunkIndex}" 的确定性 UUID（nameUUIDFromBytes，
     * RFC 4122 合法格式）。同一 fileId + chunkIndex 恒定同一 UUID（幂等 upsert）；
     * 不同 file/chunk 不冲突。不使用 randomUUID。
     */
    static String pointId(Long fileId, int chunkIndex) {
        String name = "file-" + fileId + "-c" + chunkIndex;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
