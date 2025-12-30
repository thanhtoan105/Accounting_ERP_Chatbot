package com.accounting.dto.embedding;

/**
 * Action to perform on the embedding in Pinecone.
 */
public enum EmbeddingAction {

    /**
     * Insert or update the embedding.
     * Used when entity is created or modified.
     */
    UPSERT,

    /**
     * Delete the embedding.
     * Used when entity is deleted or should no longer be searchable.
     */
    DELETE
}
