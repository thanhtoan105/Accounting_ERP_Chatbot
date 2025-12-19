-- Fix column types to match Hibernate expectations (VARCHAR instead of CHAR)
ALTER TABLE analytics_audit_log 
    ALTER COLUMN merkle_leaf_hash TYPE VARCHAR(64),
    ALTER COLUMN record_hash TYPE VARCHAR(64),
    ALTER COLUMN prev_hash TYPE VARCHAR(64);

-- Also fix audit_chain_checkpoints table
ALTER TABLE audit_chain_checkpoints 
    ALTER COLUMN merkle_root TYPE VARCHAR(64),
    ALTER COLUMN chain_head_hash TYPE VARCHAR(64),
    ALTER COLUMN chain_tail_hash TYPE VARCHAR(64);
