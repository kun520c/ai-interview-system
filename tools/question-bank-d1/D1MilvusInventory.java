import com.kun.aiinterview.knowledge.vector.milvus.*;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.index.request.*;
import io.milvus.v2.service.vector.request.QueryReq;
import java.net.URI;
import java.time.Duration;
import java.util.*;

/** Read-only, full-row inventory of the existing D1 evaluation host. Never initializes collections. */
public final class D1MilvusInventory {
    public static void main(String[] args) {
        if (args.length != 0) throw new IllegalArgumentException("No target overrides accepted");
        var p = new MilvusProperties();
        p.setUri(URI.create("http://127.0.0.1:19532"));
        p.setDatabaseName("default");
        p.setConnectTimeout(Duration.ofSeconds(5));
        p.setRequestTimeout(Duration.ofSeconds(10));
        var config = new MilvusConfiguration();
        var client = config.milvusClient(config.milvusConnectConfig(p));
        try {
            if (!Boolean.TRUE.equals(client.checkHealth().getIsHealthy())) throw new IllegalStateException("unhealthy");
            var names = client.listCollections().getCollectionNames().stream().sorted().toList();
            if (!new HashSet<>(names).equals(Set.of("e0r2_synthetic_bcfb4ee2f2d34c1091856c2205d2a161",
                    "e0r3_synthetic_6a1183deeacca38effe5a1d3b70a51f2", "knowledge_chunk_vectors_test",
                    "knowledge_chunk_vectors_evaluation"))) throw new IllegalStateException("unexpected collections: " + names);
            System.out.println("version=" + client.getServerVersion() + " database=default");
            for (String name : names) {
                if (!Boolean.TRUE.equals(client.getLoadState(GetLoadStateReq.builder().collectionName(name).build())))
                    throw new IllegalStateException("not loaded");
                var description = client.describeCollection(DescribeCollectionReq.builder().collectionName(name).build());
                if (name.endsWith("evaluation") && !Long.valueOf(470437151302223600L).equals(description.getCollectionID()))
                    throw new IllegalStateException("evaluation collection identity mismatch");
                System.out.println("collection=" + name);
                System.out.println("schema=" + description);
                System.out.println("index=" + client.describeIndex(DescribeIndexReq.builder().collectionName(name).fieldName("vector").build()));
                var count = client.query(QueryReq.builder().collectionName(name).filter("")
                        .outputFields(List.of("count(*)")).consistencyLevel(ConsistencyLevel.STRONG).build());
                long n = ((Number) count.getQueryResults().getFirst().getEntity().get("count(*)")).longValue();
                if (n != (name.endsWith("evaluation") ? 2 : 0)) throw new IllegalStateException("unexpected count");
                System.out.println("count=" + n);
                if (n != 0) {
                    var rows = client.query(QueryReq.builder().collectionName(name).filter("")
                            .outputFields(List.of("vector_id", "document_id", "chunk_index", "embedding_version", "vector"))
                            .consistencyLevel(ConsistencyLevel.STRONG).limit(3).build()).getQueryResults();
                    if (rows.size() != 2) throw new IllegalStateException("row count mismatch");
                    rows.stream().map(r -> new TreeMap<>(r.getEntity()).toString()).sorted().forEach(System.out::println);
                }
            }
        } finally { client.close(); }
    }
}
