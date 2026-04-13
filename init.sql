-- =============================================
-- ENUM TYPES
-- Note: Database 'document_management' is auto-created by POSTGRES_DB env var
-- =============================================
CREATE TYPE user_role AS ENUM ('user', 'admin');
CREATE TYPE file_type AS ENUM ('pdf', 'docx', 'txt');
CREATE TYPE visibility_type AS ENUM ('private', 'public', 'shared');
CREATE TYPE permission_type AS ENUM ('view', 'download');
CREATE TYPE notification_type AS ENUM ('share', 'system');

-- =============================================
-- 1. BẢNG users
-- =============================================
CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    avatar_url    TEXT,
    role          user_role    NOT NULL DEFAULT 'user',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    refresh_token TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- =============================================
-- 2. BẢNG folders
-- =============================================
CREATE TABLE folders (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_id   BIGINT       REFERENCES folders(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    visibility  VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC',
    share_token VARCHAR(36)  UNIQUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- =============================================
-- 3. BẢNG documents
-- =============================================
CREATE TABLE documents (
    id             BIGSERIAL       PRIMARY KEY,
    user_id        BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    folder_id      BIGINT          REFERENCES folders(id) ON DELETE CASCADE,
    title          VARCHAR(255)    NOT NULL,
    file_name      VARCHAR(255)    NOT NULL,
    file_type      file_type       NOT NULL,
    file_size      BIGINT          NOT NULL,
    file_url       TEXT            NOT NULL,
    visibility     visibility_type NOT NULL DEFAULT 'private',
    extracted_text TEXT,
    summary        TEXT,
    keywords       TEXT,
    tfidf_vector   TEXT,
    download_count INT             NOT NULL DEFAULT 0,
    created_at     TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- =============================================
-- 4. BẢNG document_shares
-- =============================================
CREATE TABLE document_shares (
    id          BIGSERIAL       PRIMARY KEY,
    document_id BIGINT          NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    shared_by   BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    shared_to   BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission  permission_type NOT NULL DEFAULT 'view',
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    UNIQUE (document_id, shared_to)
);

-- =============================================
-- 5. BẢNG document_similarities
-- =============================================
CREATE TABLE document_similarities (
    id               BIGSERIAL PRIMARY KEY,
    document_id_1    BIGINT    NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    document_id_2    BIGINT    NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    similarity_score FLOAT     NOT NULL CHECK (similarity_score >= 0 AND similarity_score <= 1),
    calculated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (document_id_1, document_id_2),
    CHECK (document_id_1 < document_id_2)
);

-- =============================================
-- 6. BẢNG search_history
-- =============================================
CREATE TABLE search_history (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    keyword      VARCHAR(255) NOT NULL,
    result_count INT          NOT NULL DEFAULT 0,
    searched_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- =============================================
-- 7. BẢNG notifications
-- =============================================
CREATE TABLE notifications (
    id           BIGSERIAL         PRIMARY KEY,
    user_id      BIGINT            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type         notification_type NOT NULL,
    title        VARCHAR(255)      NOT NULL,
    message      TEXT              NOT NULL,
    reference_id BIGINT,
    is_read      BOOLEAN           NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP         NOT NULL DEFAULT NOW()
);

-- =============================================
-- 8. BẢNG forgot_passwords (OTP-based)
-- =============================================
CREATE TABLE forgot_passwords (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    otp             INT          NOT NULL,
    is_verified     BOOLEAN      NOT NULL DEFAULT FALSE,
    expiration_time TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- =============================================
-- INDEX
-- =============================================
-- users
CREATE INDEX idx_users_email ON users(email);

-- folders
CREATE INDEX idx_folders_user_id     ON folders(user_id);
CREATE INDEX idx_folders_parent_id   ON folders(parent_id);
CREATE INDEX idx_folders_share_token ON folders(share_token);

-- documents
CREATE INDEX idx_documents_user_id    ON documents(user_id);
CREATE INDEX idx_documents_folder_id  ON documents(folder_id);
CREATE INDEX idx_documents_visibility ON documents(visibility);
CREATE INDEX idx_documents_fulltext   ON documents USING GIN(
    to_tsvector('english', coalesce(extracted_text, ''))
);

-- document_shares
CREATE INDEX idx_shares_document_id ON document_shares(document_id);
CREATE INDEX idx_shares_shared_to   ON document_shares(shared_to);

-- document_similarities
CREATE INDEX idx_similarities_doc1 ON document_similarities(document_id_1);
CREATE INDEX idx_similarities_doc2 ON document_similarities(document_id_2);

-- search_history
CREATE INDEX idx_search_history_user_id ON search_history(user_id);

-- notifications
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_is_read  ON notifications(user_id, is_read);

-- forgot_passwords
CREATE INDEX idx_forgot_passwords_user_id ON forgot_passwords(user_id);
CREATE INDEX idx_forgot_passwords_otp_user ON forgot_passwords(otp, user_id);

-- =============================================
-- TRIGGER tự động cập nhật updated_at
-- =============================================
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_folders_updated_at
    BEFORE UPDATE ON folders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_documents_updated_at
    BEFORE UPDATE ON documents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
