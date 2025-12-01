-- Create chatbot queries table
-- Stores all chatbot query interactions with RAG-based responses
-- Implements company-scoped audit logging for AI assistance

CREATE TABLE chatbot_queries (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    query_text TEXT NOT NULL,
    answer_text TEXT,
    citations JSONB,
    confidence_score FLOAT,
    session_id VARCHAR(100) NOT NULL,
    language VARCHAR(10) NOT NULL DEFAULT 'vi',
    response_time_ms INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    audit_hash VARCHAR(64),

    -- Foreign key constraints
    CONSTRAINT fk_chatbot_queries_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_chatbot_queries_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    -- Validation constraints
    CONSTRAINT chk_chatbot_confidence_score CHECK (confidence_score IS NULL OR (confidence_score >= 0 AND confidence_score <= 1)),
    CONSTRAINT chk_chatbot_response_time CHECK (response_time_ms IS NULL OR response_time_ms >= 0)
);

-- Create indexes for efficient querying
CREATE INDEX idx_chatbot_queries_company_id ON chatbot_queries(company_id);
CREATE INDEX idx_chatbot_queries_user_id ON chatbot_queries(user_id);
CREATE INDEX idx_chatbot_queries_session_id ON chatbot_queries(session_id);
CREATE INDEX idx_chatbot_queries_created_at ON chatbot_queries(created_at DESC);
CREATE INDEX idx_chatbot_queries_company_created ON chatbot_queries(company_id, created_at DESC);

-- Comments on table
COMMENT ON TABLE chatbot_queries IS 'RAG-based chatbot query audit log. Tracks all AI-assisted voucher queries with citations and confidence scores for compliance.';

-- Comments on key columns
COMMENT ON COLUMN chatbot_queries.query_text IS 'Original user query in natural language (Vietnamese or English)';
COMMENT ON COLUMN chatbot_queries.answer_text IS 'RAG-generated response from LLM with voucher context';
COMMENT ON COLUMN chatbot_queries.citations IS 'JSONB array of voucher citations with entity_type, entity_id, voucher_number, excerpt, relevance_score, and link';
COMMENT ON COLUMN chatbot_queries.confidence_score IS 'Confidence score between 0-1 based on citation relevance and retrieval quality';
COMMENT ON COLUMN chatbot_queries.session_id IS 'Session identifier for grouping related queries in a conversation thread';
COMMENT ON COLUMN chatbot_queries.language IS 'Query language (vi=Vietnamese, en=English) for response localization';
COMMENT ON COLUMN chatbot_queries.response_time_ms IS 'Total query processing time in milliseconds (embedding + retrieval + LLM generation)';
COMMENT ON COLUMN chatbot_queries.audit_hash IS 'SHA-256 hash of query + citations for tamper detection';

-- Create placeholder table for chatbot feedback (Story 9.4)
CREATE TABLE chatbot_feedback (
    id BIGSERIAL PRIMARY KEY,
    query_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating INTEGER,
    feedback_text TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_chatbot_feedback_query FOREIGN KEY (query_id) REFERENCES chatbot_queries(id) ON DELETE CASCADE,
    CONSTRAINT fk_chatbot_feedback_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_chatbot_feedback_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_chatbot_feedback_rating CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5))
);

CREATE INDEX idx_chatbot_feedback_query_id ON chatbot_feedback(query_id);
CREATE INDEX idx_chatbot_feedback_company_id ON chatbot_feedback(company_id);

COMMENT ON TABLE chatbot_feedback IS 'User feedback on chatbot responses (placeholder for Story 9.4 - not yet implemented)';

-- Create placeholder table for guardrail logs (Story 9.1)
CREATE TABLE guardrail_logs (
    id BIGSERIAL PRIMARY KEY,
    query_id BIGINT,
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    violation_type VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    details JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_guardrail_logs_query FOREIGN KEY (query_id) REFERENCES chatbot_queries(id) ON DELETE SET NULL,
    CONSTRAINT fk_guardrail_logs_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_guardrail_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_guardrail_logs_company_id ON guardrail_logs(company_id);
CREATE INDEX idx_guardrail_logs_created_at ON guardrail_logs(created_at DESC);

COMMENT ON TABLE guardrail_logs IS 'Logs for chatbot safety guardrails and policy violations (placeholder for Story 9.1 - not yet implemented)';
