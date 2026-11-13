package com.kun.aiinterview.knowledge.vector.milvus;

import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.knowledge.vector.VectorWriteItem;
import io.milvus.grpc.*;
import io.milvus.shaded.io.grpc.Context;
import io.milvus.shaded.io.grpc.Server;
import io.milvus.shaded.io.grpc.stub.StreamObserver;
import io.milvus.shaded.io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.RetryConfig;
import io.milvus.v2.exception.MilvusClientException;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Real shaded SDK and loopback gRPC transport; never connects to a Milvus server. */
class MilvusGrpcDeadlineTest {
    private final SyntheticMilvus service = new SyntheticMilvus();
    private Server server;
    private MilvusClientV2 client;
    private MilvusProperties properties;

    @BeforeEach
    void start() throws Exception {
        server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(service).build().start();
        properties = new MilvusProperties();
        properties.setUri(URI.create("http://127.0.0.1:" + server.getPort()));
        properties.setDatabaseName("default");
        properties.setCollectionName("synthetic_vectors");
        properties.setDimension(3);
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setRequestTimeout(Duration.ofMillis(300));
    }

    @AfterEach
    void close() throws Exception {
        if (client != null) client.close();
        server.shutdownNow();
        assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void rawSdkDatabaseProbeHasNoDeadlineEvenWithBothTimeoutsConfigured() throws Exception {
        AtomicReference<Boolean> hasDeadline = new AtomicReference<>();
        service.database = (request, observer) -> {
            hasDeadline.set(Context.current().getDeadline() != null);
            success(observer, databases());
        };
        client = new MilvusClientV2(config());
        assertThat(hasDeadline).hasValue(false);
    }

    @Test
    void factoryBoundsStalledConstructorDatabaseProbe() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicReference<StreamObserver<ListDatabasesResponse>> pending = new AtomicReference<>();
        AtomicReference<Boolean> hasDeadline = new AtomicReference<>();
        service.database = (request, observer) -> {
            pending.set(observer);
            hasDeadline.set(Context.current().getDeadline() != null);
            Context.current().addListener(context -> cancelled.countDown(), Runnable::run);
            entered.countDown();
        };
        properties.setConnectTimeout(Duration.ofMillis(800));
        long start = System.nanoTime();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var result = executor.submit(() -> catchThrowable(() -> client = new MilvusConfiguration().milvusClient(config())));
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            // This assertion fails before the fix, without waiting forever for the raw SDK.
            assertThat(hasDeadline).hasValue(true);
            assertThat(result.get(4, TimeUnit.SECONDS)).isInstanceOf(RuntimeException.class);
            assertThat(cancelled.await(2, TimeUnit.SECONDS)).isTrue();
            record("constructor-database-timeout", service.databaseCalls.get(), start);
            assertThat(service.databaseCalls).hasValue(1);
        } finally {
            if (pending.get() != null) pending.get().onError(io.milvus.shaded.io.grpc.Status.CANCELLED.asRuntimeException());
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void configuredUnavailableSearchFailsOnceAndNeverBecomesEmptyEvidence() {
        configuredClient();
        service.search = (request, observer) -> observer.onError(io.milvus.shaded.io.grpc.Status.UNAVAILABLE.asRuntimeException());
        long start = System.nanoTime();
        assertThatThrownBy(() -> adapter().search(List.of(1F, 0F, 0F), "v1", 5))
                .isInstanceOf(ExternalServiceException.class).hasCauseInstanceOf(MilvusClientException.class);
        record("configured-unavailable-search", service.searchCalls.get(), start);
        assertThat(service.searchCalls).hasValue(1);
    }

    @Test
    void configuredServerRateLimitFailsOnce() {
        configuredClient();
        service.search = (request, observer) -> success(observer, SearchResults.newBuilder()
                .setStatus(Status.newBuilder().setCode(8).setErrorCode(ErrorCode.RateLimit)).build());
        long start = System.nanoTime();
        assertThatThrownBy(() -> adapter().search(List.of(1F, 0F, 0F), "v1", 5))
                .isInstanceOf(ExternalServiceException.class).hasCauseInstanceOf(MilvusClientException.class);
        record("configured-server-rate-limit", service.searchCalls.get(), start);
        assertThat(service.searchCalls).hasValue(1);
    }

    @Test
    void actualGrpcRetrySucceedsButExceedsOneAttemptDeadline() {
        properties.setRequestTimeout(Duration.ofMillis(500));
        client = new MilvusClientV2(config());
        client.retryConfig(RetryConfig.builder().maxRetryTimes(4).initialBackOffMs(5)
                .maxBackOffMs(5).backOffMultiplier(1).build());
        List<Long> deadlines = Collections.synchronizedList(new ArrayList<>());
        var responses = Executors.newSingleThreadScheduledExecutor();
        try {
            service.exists = (request, observer) -> {
                var deadline = Context.current().getDeadline();
                assertThat(deadline).isNotNull();
                deadlines.add(System.nanoTime() + deadline.timeRemaining(TimeUnit.NANOSECONDS));
                int attempt = service.existsCalls.get();
                responses.schedule(() -> {
                    if (attempt < 3) observer.onError(io.milvus.shaded.io.grpc.Status.UNAVAILABLE.asRuntimeException());
                    else success(observer, BoolResponse.newBuilder().setValue(true).build());
                }, 200, TimeUnit.MILLISECONDS);
            };
            long start = System.nanoTime();
            assertThat(client.hasCollection(HasCollectionReq.builder().collectionName("synthetic_vectors").build())).isTrue();
            record("actual-grpc-third-success", service.existsCalls.get(), start);
            assertThat(service.existsCalls).hasValue(3);
            assertThat(System.nanoTime() - start).isGreaterThan(500_000_000L);
            assertThat(deadlines).hasSize(3);
            assertThat(deadlines.get(1)).isGreaterThan(deadlines.get(0));
            assertThat(deadlines.get(2)).isGreaterThan(deadlines.get(1));
        } finally {
            responses.shutdownNow();
        }
    }

    @Test
    void rawSdkExhaustedExistenceCheckReturnsNull() {
        client = new MilvusClientV2(config());
        client.retryConfig(RetryConfig.builder().maxRetryTimes(2).initialBackOffMs(1).build());
        service.exists = (request, observer) -> observer.onError(io.milvus.shaded.io.grpc.Status.UNAVAILABLE.asRuntimeException());
        long start = System.nanoTime();
        assertThat(client.hasCollection(HasCollectionReq.builder().collectionName("synthetic_vectors").build())).isNull();
        record("raw-sdk-existence-null", service.existsCalls.get(), start);
        assertThat(service.existsCalls).hasValue(2);
    }

    @Test
    void rawSdkExhaustedVoidCreateSilentlyReturns() {
        client = new MilvusClientV2(config());
        client.retryConfig(RetryConfig.builder().maxRetryTimes(2).initialBackOffMs(1).build());
        service.exists = (request, observer) -> success(observer, BoolResponse.newBuilder().setValue(false).build());
        long start = System.nanoTime();
        // Deliberately use the raw SDK policy to characterize the void-response hole.
        new MilvusCollectionInitializer(client, properties).run(null);
        record("raw-sdk-create-silent-failure", service.createCalls.get(), start);
        assertThat(service.createCalls).hasValue(2);
    }

    @Test
    void configuredDeadlineCancelsTransportButCannotUndoLateServerWrite() throws Exception {
        configuredClient();
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicReference<StreamObserver<MutationResult>> pending = new AtomicReference<>();
        service.insert = (request, observer) -> {
            pending.set(observer);
            Context.current().addListener(context -> cancelled.countDown(), Runnable::run);
        };
        long start = System.nanoTime();
        assertThatThrownBy(() -> adapter().insert(items())).isInstanceOf(ExternalServiceException.class)
                .hasCauseInstanceOf(MilvusClientException.class);
        assertThat(cancelled.await(2, TimeUnit.SECONDS)).isTrue();
        record("insert-deadline", service.insertCalls.get(), start);
        assertThat(service.insertCalls).hasValue(1);
        // Compensation can run BEFORE a non-cooperative server completes its original write.
        adapter().deleteByVectorIds(List.of("attempt-1"));
        service.stored.add("attempt-1");
        success(pending.get(), MutationResult.newBuilder().setInsertCnt(1).build());
        assertThat(service.stored).contains("attempt-1");
        adapter().deleteByVectorIds(List.of("attempt-1"));
        assertThat(service.stored).containsExactly("unrelated");
        assertThat(service.deleteCalls).hasValue(2);
    }

    @Test
    void lostInsertAcknowledgementStillRequiresTargetedIdempotentCompensation() {
        configuredClient();
        service.insert = (request, observer) -> {
            service.stored.add("attempt-1");
            observer.onError(io.milvus.shaded.io.grpc.Status.UNAVAILABLE.asRuntimeException());
        };
        long start = System.nanoTime();
        assertThatThrownBy(() -> adapter().insert(items())).isInstanceOf(ExternalServiceException.class);
        assertThat(service.stored).containsExactlyInAnyOrder("attempt-1", "unrelated");
        adapter().deleteByVectorIds(List.of("attempt-1"));
        adapter().deleteByVectorIds(List.of("attempt-1"));
        assertThat(service.stored).containsExactly("unrelated");
        record("lost-ack-insert", service.insertCalls.get(), start);
        assertThat(service.insertCalls).hasValue(1);
        assertThat(service.deleteCalls).hasValue(2);
    }

    @Test
    void interruptionCancelsActiveRpcWithoutSdkRetry() throws Exception {
        configuredClient();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        properties.setRequestTimeout(Duration.ofSeconds(5));
        client.withTimeout(5, TimeUnit.SECONDS);
        service.search = (request, observer) -> {
            Context.current().addListener(context -> cancelled.countDown(), Runnable::run);
            entered.countDown();
        };
        Thread caller = new Thread(() -> {
            failure.set(catchThrowable(() -> adapter().search(List.of(1F, 0F, 0F), "v1", 5)));
            interrupted.set(Thread.currentThread().isInterrupted());
        }, "b4d-interrupt-test");
        long start = System.nanoTime();
        try {
            caller.start();
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            caller.join(3000);
            assertThat(caller.isAlive()).isFalse();
            assertThat(failure.get()).isInstanceOf(ExternalServiceException.class);
            assertThat(interrupted).hasValue(true);
            assertThat(cancelled.await(2, TimeUnit.SECONDS)).isTrue();
            record("interrupted-search", service.searchCalls.get(), start);
            assertThat(service.searchCalls).hasValue(1);
        } finally {
            caller.interrupt();
            caller.join(6000);
        }
    }

    @Test
    void initializerDoesNotCreateWhenExistenceRpcFails() {
        configuredClient();
        service.exists = (request, observer) -> observer.onError(io.milvus.shaded.io.grpc.Status.UNAVAILABLE.asRuntimeException());
        assertThatThrownBy(() -> new MilvusCollectionInitializer(client, properties).run(null))
                .isInstanceOf(MilvusClientException.class);
        assertThat(service.existsCalls).hasValue(1);
        assertThat(service.createCalls).hasValue(0);
    }

    @Test
    void initializerDoesNotSilentlySucceedWhenCreateRpcFails() {
        configuredClient();
        service.exists = (request, observer) -> success(observer, BoolResponse.newBuilder().setValue(false).build());
        assertThatThrownBy(() -> new MilvusCollectionInitializer(client, properties).run(null))
                .isInstanceOf(MilvusClientException.class);
        assertThat(service.existsCalls).hasValue(1);
        assertThat(service.createCalls).hasValue(1);
    }

    private void configuredClient() {
        client = new MilvusConfiguration().milvusClient(config());
        // Fail fast before executing a 75-attempt production regression on the pre-fix factory.
        Object rpc = ReflectionTestUtils.getField(client, "rpcUtils");
        RetryConfig retry = (RetryConfig) ReflectionTestUtils.getField(rpc, "retryConfig");
        assertThat(retry.getMaxRetryTimes()).isEqualTo(1);
    }

    private ConnectConfig config() { return new MilvusConfiguration().milvusConnectConfig(properties); }
    private MilvusVectorStoreClient adapter() { return new MilvusVectorStoreClient(client, properties); }
    private List<VectorWriteItem> items() { return List.of(new VectorWriteItem("attempt-1", 1L, 1, "v1", List.of(1F, 0F, 0F))); }
    private static ListDatabasesResponse databases() { return ListDatabasesResponse.newBuilder().addDbNames("default").build(); }
    private static <T> void success(StreamObserver<T> observer, T response) { observer.onNext(response); observer.onCompleted(); }
    private static void record(String scenario, int attempts, long start) {
        System.out.printf("B4D gRPC scenario=%s attempts=%d elapsedMs=%d%n", scenario, attempts, (System.nanoTime() - start) / 1_000_000);
    }

    private static class SyntheticMilvus extends MilvusServiceGrpc.MilvusServiceImplBase {
        final AtomicInteger databaseCalls = new AtomicInteger();
        final AtomicInteger searchCalls = new AtomicInteger();
        final AtomicInteger existsCalls = new AtomicInteger();
        final AtomicInteger createCalls = new AtomicInteger();
        final AtomicInteger insertCalls = new AtomicInteger();
        final AtomicInteger deleteCalls = new AtomicInteger();
        final Set<String> stored = ConcurrentHashMap.newKeySet();
        BiConsumer<ListDatabasesRequest, StreamObserver<ListDatabasesResponse>> database = (request, observer) -> success(observer, databases());
        BiConsumer<SearchRequest, StreamObserver<SearchResults>> search;
        BiConsumer<HasCollectionRequest, StreamObserver<BoolResponse>> exists;
        BiConsumer<InsertRequest, StreamObserver<MutationResult>> insert;
        SyntheticMilvus() { stored.add("unrelated"); }
        @Override public void connect(ConnectRequest request, StreamObserver<ConnectResponse> observer) {
            success(observer, ConnectResponse.newBuilder().setIdentifier(1).build());
        }
        @Override public void listDatabases(ListDatabasesRequest request, StreamObserver<ListDatabasesResponse> observer) {
            databaseCalls.incrementAndGet(); database.accept(request, observer);
        }
        @Override public void search(SearchRequest request, StreamObserver<SearchResults> observer) {
            searchCalls.incrementAndGet(); search.accept(request, observer);
        }
        @Override public void hasCollection(HasCollectionRequest request, StreamObserver<BoolResponse> observer) {
            existsCalls.incrementAndGet(); exists.accept(request, observer);
        }
        @Override public void createCollection(CreateCollectionRequest request, StreamObserver<Status> observer) {
            createCalls.incrementAndGet(); observer.onError(io.milvus.shaded.io.grpc.Status.UNAVAILABLE.asRuntimeException());
        }
        @Override public void describeCollection(DescribeCollectionRequest request, StreamObserver<DescribeCollectionResponse> observer) {
            CollectionSchema schema = CollectionSchema.newBuilder().setName("synthetic_vectors")
                    .addFields(FieldSchema.newBuilder().setFieldID(100).setName("vector_id").setDataType(DataType.VarChar).setIsPrimaryKey(true)
                            .addTypeParams(KeyValuePair.newBuilder().setKey("max_length").setValue("64")))
                    .addFields(FieldSchema.newBuilder().setFieldID(101).setName("document_id").setDataType(DataType.Int64))
                    .addFields(FieldSchema.newBuilder().setFieldID(102).setName("chunk_index").setDataType(DataType.Int64))
                    .addFields(FieldSchema.newBuilder().setFieldID(103).setName("embedding_version").setDataType(DataType.VarChar)
                            .addTypeParams(KeyValuePair.newBuilder().setKey("max_length").setValue("128")))
                    .addFields(FieldSchema.newBuilder().setFieldID(104).setName("vector").setDataType(DataType.FloatVector)
                            .addTypeParams(KeyValuePair.newBuilder().setKey("dim").setValue("3"))).build();
            success(observer, DescribeCollectionResponse.newBuilder().setSchema(schema).build());
        }
        @Override public void insert(InsertRequest request, StreamObserver<MutationResult> observer) {
            insertCalls.incrementAndGet(); insert.accept(request, observer);
        }
        @Override public void delete(DeleteRequest request, StreamObserver<MutationResult> observer) {
            deleteCalls.incrementAndGet();
            assertThat(request.getExpr()).isEqualTo("vector_id in {vectorIds}");
            assertThat(request.getExprTemplateValuesOrThrow("vectorIds").getArrayVal().getStringData().getDataList())
                    .containsExactly("attempt-1");
            success(observer, MutationResult.newBuilder().setDeleteCnt(stored.remove("attempt-1") ? 1 : 0).build());
        }
    }
}
