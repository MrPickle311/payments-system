CREATE TABLE IF NOT EXISTS wallet_accounts (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    balance NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    currency TEXT NOT NULL CHECK (char_length(currency) = 3),
    CONSTRAINT chk_positive_balance CHECK (balance >= 0)
);

CREATE INDEX IF NOT EXISTS idx_wallet_accounts_user_id ON wallet_accounts(user_id);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
