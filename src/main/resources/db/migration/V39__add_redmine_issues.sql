CREATE TABLE zoomos_redmine_issues (
    id           BIGSERIAL    PRIMARY KEY,
    site_name    VARCHAR(255) NOT NULL UNIQUE,
    issue_id     INTEGER      NOT NULL,
    issue_status VARCHAR(100),
    issue_url    VARCHAR(500),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);
