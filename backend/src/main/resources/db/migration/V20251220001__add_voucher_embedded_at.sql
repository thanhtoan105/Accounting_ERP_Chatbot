-- Add embedded_at column to track RAG embedding status
-- This column is set when a voucher is successfully embedded into Pinecone for the RAG chatbot

ALTER TABLE vouchers ADD COLUMN embedded_at TIMESTAMP WITH TIME ZONE;

-- Partial index for efficient batch queries (find unembedded posted vouchers)
-- This index only includes rows where status='posted' AND embedded_at IS NULL
CREATE INDEX idx_vouchers_unembedded_posted ON vouchers(created_at ASC) 
  WHERE status = 'posted' AND embedded_at IS NULL;

-- Comment for documentation
COMMENT ON COLUMN vouchers.embedded_at IS 'Timestamp when voucher was embedded into Pinecone for RAG chatbot. NULL means not yet embedded.';

