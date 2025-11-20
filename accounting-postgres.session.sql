SELECT *
FROM users
INSERT INTO users (
        email,
        password_hash,
        full_name,
        role,
        company_id,
        created_at,
        updated_at
    )
VALUES (
        'admin@example.com',
        crypt('password', gen_salt('bf')),
        'Admin User',
        'admin',
        1,
        NOW(),
        NOW()
    );
INSERT INTO users (
        email,
        password_hash,
        full_name,
        role,
        company_id,
        created_at,
        updated_at
    )
VALUES (
        'accountant@example.com',
        crypt('password', gen_salt('bf')),
        'Accountant User',
        'accountant',
        1,
        NOW(),
        NOW()
    );
INSERT INTO users (
        email,
        password_hash,
        full_name,
        role,
        company_id,
        created_at,
        updated_at
    )
VALUES (
        'chief@example.com',
        crypt('password', gen_salt('bf')),
        'Chief User',
        'chief_accountant',
        1,
        NOW(),
        NOW()
    );
INSERT INTO users (
        email,
        password_hash,
        full_name,
        role,
        company_id,
        created_at,
        updated_at
    )
VALUES (
        'cfo@example.com',
        crypt('password', gen_salt('bf')),
        'CFO User',
        'cfo',
        1,
        NOW(),
        NOW()
    );
DELETE FROM flyway_schema_history
WHERE version = '14';
SELECT *
FROM companies;
SELECT indexname,
    tablename
FROM pg_indexes
WHERE schemaname = 'accounting'
    AND indexname IN (
        'idx_invitations_created_by',
        'idx_users_company_id',
        'idx_vouchers_reversed_by'
    );
SELECT *
FROM chart_of_accounts;