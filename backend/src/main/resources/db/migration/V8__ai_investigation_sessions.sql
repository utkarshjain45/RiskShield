-- V8: AI Investigation Assistant Sessions & Messages
-- Stores conversation history, tool calls, and sources for merchant risk analysts

CREATE TABLE IF NOT EXISTS ai_investigation_sessions (
    id VARCHAR(64) PRIMARY KEY,
    merchant_id VARCHAR(64),
    title VARCHAR(255) NOT NULL,
    user_id VARCHAR(64) DEFAULT 'ANALYST_DEFAULT' NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_investigation_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL REFERENCES ai_investigation_sessions(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL, -- USER, ASSISTANT, SYSTEM, TOOL
    content TEXT NOT NULL,
    tool_calls TEXT,           -- JSON array of tool invocations
    tool_call_id VARCHAR(64),
    sources TEXT,              -- JSON array of data sources referenced
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_messages_session ON ai_investigation_messages(session_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_ai_sessions_merchant ON ai_investigation_sessions(merchant_id, updated_at DESC);
