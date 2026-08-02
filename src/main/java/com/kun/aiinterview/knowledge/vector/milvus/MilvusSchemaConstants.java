package com.kun.aiinterview.knowledge.vector.milvus;

final class MilvusSchemaConstants {

    static final String VECTOR_ID_FIELD = "vector_id";
    static final String DOCUMENT_ID_FIELD = "document_id";
    static final String CHUNK_INDEX_FIELD = "chunk_index";
    static final String EMBEDDING_VERSION_FIELD = "embedding_version";
    static final String VECTOR_FIELD = "vector";

    static final int VECTOR_ID_MAX_LENGTH = 64;
    static final int EMBEDDING_VERSION_MAX_LENGTH = 128;

    private MilvusSchemaConstants() {
    }
}