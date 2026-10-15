import com.fasterxml.jackson.databind.JsonNode;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.RetryConfig;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Separate read-only diagnostic: no HTTP transport, search, load or business writes. */
public final class P42ResourcePreflight {
    public static void main(String[] args) throws Exception {
        P42R02ExecuteOnce.require(args.length == 1, "PREFLIGHT_OUTPUT_REQUIRED");
        Path out = Path.of(args[0]);
        Files.createDirectory(out);
        JsonNode input = P42R02ExecuteOnce.JSON.readTree(System.in.readNBytes(65536));
        String password = input.path("dbPassword").asText(), token = input.path("milvusToken").asText();
        String phase = "OFFLINE_FREEZE";
        var report = P42R02ExecuteOnce.JSON.createObjectNode().put("startedUtc", Instant.now().toString())
                .put("models", 0).put("milvusSearch", 0).put("mysqlWrites", 0).put("milvusWrites", 0)
                .put("sdk", "io.milvus:milvus-sdk-java:2.6.20").put("java", System.getProperty("java.version"))
                .put("uri", "http://127.0.0.1:19530").put("connectTimeoutMs", 5000).put("rpcDeadlineMs", 20000);
        MilvusClientV2 client = null;
        try {
            // Before the new execution seal exists, verify the preserved, already sealed baseline.
            P42ExecuteOnce.verifyFrozen();
            P42R02ExecuteOnce.isolation();
            phase = "MILVUS_CLIENT_CONSTRUCT";
            var config = ConnectConfig.builder().uri("http://127.0.0.1:19530").dbName("default")
                    .connectTimeoutMs(5000).rpcDeadlineMs(20000);
            if (!token.isBlank()) config.token(token);
            client = new MilvusClientV2(config.build());
            client.retryConfig(RetryConfig.builder().maxRetryTimes(1).retryOnRateLimit(false).build());
            report.put("grpc", "PASS");
            phase = "MILVUS_DATABASE_LIST";
            var databases = P42R02ExecuteOnce.JSON.valueToTree(client.listDatabases());
            P42R02ExecuteOnce.require(databases.path("databaseNames").isArray(), "DATABASE_LIST_FORMAT");
            boolean present = false;
            for (var name : databases.path("databaseNames")) present |= name.asText().equals("default");
            P42R02ExecuteOnce.require(present, "DEFAULT_DATABASE_MISSING");
            report.put("defaultDatabase", "PASS");
            phase = "MILVUS_COLLECTION_SCHEMA_INDEX_LOAD";
            P42R02ExecuteOnce.require(Boolean.TRUE.equals(client.hasCollection(HasCollectionReq.builder()
                    .collectionName(IndependentRetrieval.COLLECTION).build())), "FROZEN_COLLECTION_MISSING");
            var description = client.describeCollection(DescribeCollectionReq.builder().collectionName(IndependentRetrieval.COLLECTION).build());
            var index = client.describeIndex(DescribeIndexReq.builder().collectionName(IndependentRetrieval.COLLECTION).fieldName("vector").build());
            IndependentRetrieval.validateSchema(description, index);
            boolean loaded = Boolean.TRUE.equals(client.getLoadState(GetLoadStateReq.builder().collectionName(IndependentRetrieval.COLLECTION).build()));
            P42R02ExecuteOnce.require(loaded, "FROZEN_COLLECTION_NOT_LOADED");
            report.put("collection", IndependentRetrieval.COLLECTION).put("collectionId", Long.toString(description.getCollectionID()))
                    .put("dimension", 1024).put("loaded", loaded).put("schemaIndex", "PASS");
            P42R02ExecuteOnce.writeJson(out.resolve("milvus-metadata.json"), Map.of("description", description, "index", index));
            client.close(); client = null;
            phase = "MYSQL_AND_FROZEN_VECTOR_RESOURCE_PREFLIGHT";
            P42R02ExecuteOnce.require(!password.isBlank(), "EXISTING_RESTRICTED_DB_CREDENTIAL_REQUIRED");
            // Exactly the real execution's existing resource loader, without calling searchAndHydrate.
            try (var resources = new P3ReadOnlyRetrieval.NativeResources(password, token)) {
                P42R02ExecuteOnce.writeJson(out.resolve("resources-before.json"), resources.before());
                P42R02ExecuteOnce.writeJson(out.resolve("resources-after.json"), resources.after());
            }
            report.put("mysqlReadOnly", "PASS").put("frozen191Rows", "PASS").put("frozen52Vectors", "PASS")
                    .put("javaResourcePreflight", "PASS").put("status", "PASS");
        } catch (Exception error) {
            report.put("status", "BLOCKED").put("failureCode", P42R02ExecuteOnce.code(error));
            report.set("failureDiagnostics", P42R02ExecuteOnce.failureDiagnostics(error, phase));
        } finally {
            if (client != null) client.close();
            report.put("endedUtc", Instant.now().toString());
            P42R02ExecuteOnce.writeJson(out.resolve("result.json"), report);
            System.out.println(report);
        }
        if (!report.path("status").asText().equals("PASS")) System.exit(1);
    }
}
