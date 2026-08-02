package com.kun.aiinterview.knowledge.embedding;

import java.util.List;

public interface EmbeddingClient {
    EmbeddingBatchResult embed(List<String> texts);
}