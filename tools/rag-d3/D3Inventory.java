import com.kun.aiinterview.knowledge.vector.milvus.MilvusConfiguration;
import com.kun.aiinterview.knowledge.vector.milvus.MilvusProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.vector.request.QueryReq;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class D3Inventory {
    static final String COLLECTION = "knowledge_chunk_vectors_evaluation";
    static final String MILVUS = "http://127.0.0.1:19532";
    static final long COLLECTION_ID = 470437151302223600L;
    static final String MODEL = "qwen3.7-text-embedding";
    static final String PROFILE = MODEL + "-1024-dense-v1";
    static final String MYSQL_UUID = "85dd1e00-cf98-11f1-8677-9eb9380f25e7";
    static final String MYSQL = "jdbc:mysql://127.0.0.1:3308/ai_interview_evaluation"
            + "?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=20000";

    record Vector(long documentId, int chunkIndex, String profile, String float32Sha256) {}
    record Inventory(long collectionId, int dimension, String metric, String indexType,
                     Map<String, Vector> vectors, Map<String, Long> otherCollectionCounts) {}

    private D3Inventory() {}

    public static void main(String[] args) throws Exception {
        D3Corpus.require(args.length == 1, "INVENTORY_OUTPUT");
        var client = client();
        try {
            Inventory inventory = inventory(client);
            D3Corpus.writeNew(Path.of(args[0]), inventory);
            System.out.println("strongVectorCount=" + inventory.vectors().size());
        } finally {
            client.close();
        }
    }

    static MilvusClientV2 client() {
        var config = new MilvusConfiguration();
        var properties = new MilvusProperties();
        properties.setUri(URI.create(MILVUS));
        properties.setDatabaseName("default");
        properties.setCollectionName(COLLECTION);
        properties.setDimension(1024);
        properties.setConnectTimeout(Duration.ofSeconds(8));
        properties.setRequestTimeout(Duration.ofSeconds(20));
        return config.milvusClient(config.milvusConnectConfig(properties));
    }

    static Inventory inventory(MilvusClientV2 client) {
        D3Corpus.require(Boolean.TRUE.equals(client.checkHealth().getIsHealthy()), "MILVUS_HEALTH");
        var desc = client.describeCollection(DescribeCollectionReq.builder().databaseName("default")
                .collectionName(COLLECTION).build());
        D3Corpus.require("default".equals(desc.getDatabaseName()) && COLLECTION.equals(desc.getCollectionName())
                && desc.getCollectionID() == COLLECTION_ID, "MILVUS_IDENTITY");
        var vectorField = desc.getCollectionSchema().getField("vector");
        D3Corpus.require(vectorField.getDimension() == 1024, "MILVUS_SCHEMA_DIMENSION");
        var index = client.describeIndex(DescribeIndexReq.builder().databaseName("default")
                .collectionName(COLLECTION).fieldName("vector").build()).getIndexDescriptions().getFirst();
        D3Corpus.require("COSINE".equals(index.getMetricType().name())
                && "AUTOINDEX".equals(index.getIndexType().name()), "MILVUS_INDEX");
        long count = count(client, COLLECTION);
        D3Corpus.require(count <= 2048, "UNEXPECTED_COLLECTION_SIZE");
        Map<String, Vector> vectors = new TreeMap<>();
        var response = client.query(QueryReq.builder().databaseName("default").collectionName(COLLECTION)
                .filter("").limit((int) count + 1).consistencyLevel(ConsistencyLevel.STRONG)
                .outputFields(List.of("vector_id", "document_id", "chunk_index", "embedding_version", "vector"))
                .build());
        for (var row : response.getQueryResults()) {
            var fields = row.getEntity();
            String id = (String) fields.get("vector_id");
            Vector vector = new Vector(number(fields, "document_id").longValue(),
                    number(fields, "chunk_index").intValue(), (String) fields.get("embedding_version"),
                    vectorDigest((List<?>) fields.get("vector")));
            D3Corpus.require(vectors.putIfAbsent(id, vector) == null, "DUPLICATE_MILVUS_ID");
        }
        D3Corpus.require(vectors.size() == count, "MILVUS_COUNT_INCONSISTENT");
        Map<String, Long> other = new TreeMap<>();
        for (String name : client.listCollections()
                .getCollectionNames()) {
            if (!COLLECTION.equals(name)) {
                other.put(name, count(client, name));
            }
        }
        return new Inventory(COLLECTION_ID, 1024, index.getMetricType().name(), index.getIndexType().name(), vectors, other);
    }

    static String vectorDigest(List<?> values) {
        D3Corpus.require(values.size() == 1024, "VECTOR_DIMENSION");
        var bytes = ByteBuffer.allocate(1024 * Float.BYTES);
        for (Object value : values) {
            float number = ((Number) value).floatValue();
            D3Corpus.require(Float.isFinite(number), "VECTOR_NOT_FINITE");
            bytes.putFloat(number);
        }
        return D3Corpus.sha(bytes.array());
    }

    private static long count(MilvusClientV2 client, String collection) {
        var result = client.query(QueryReq.builder().databaseName("default").collectionName(collection)
                .filter("").outputFields(List.of("count(*)")).consistencyLevel(ConsistencyLevel.STRONG).build());
        return number(result.getQueryResults().getFirst().getEntity(), "count(*)").longValue();
    }

    static Number number(Map<String, Object> row, String field) {
        return (Number) row.get(field);
    }
}
