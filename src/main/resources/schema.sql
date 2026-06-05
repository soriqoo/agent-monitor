CREATE TABLE IF NOT EXISTS monitored_service (
    id BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    environment VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS monitored_service_service_env_ux
    ON monitored_service (service_name, environment);

CREATE TABLE IF NOT EXISTS service_check_history (
    id BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL,
    environment VARCHAR(50) NOT NULL,
    health_status VARCHAR(50) NOT NULL,
    run_status VARCHAR(50),
    last_run_date VARCHAR(20),
    response_time_ms BIGINT,
    error TEXT,
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS service_current_status (
    service_name VARCHAR(100) NOT NULL,
    environment VARCHAR(50) NOT NULL,
    health_status VARCHAR(50) NOT NULL,
    run_status VARCHAR(50),
    last_run_date VARCHAR(20),
    last_success_at TIMESTAMP WITH TIME ZONE,
    last_checked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    error TEXT,
    PRIMARY KEY (service_name, environment)
);

CREATE TABLE IF NOT EXISTS incident (
    id BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL,
    environment VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT
);

CREATE TABLE IF NOT EXISTS alert_event (
    id BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL,
    environment VARCHAR(50) NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS retention_run_history (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(30) NOT NULL,
    retention_days BIGINT NOT NULL,
    deleted_service_checks INTEGER NOT NULL DEFAULT 0,
    deleted_alert_events INTEGER NOT NULL DEFAULT 0,
    deleted_resolved_incidents INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    error TEXT
);

CREATE TABLE IF NOT EXISTS operator_action_event (
    id BIGSERIAL PRIMARY KEY,
    action_type VARCHAR(50) NOT NULL,
    target_service_name VARCHAR(100),
    target_environment VARCHAR(50),
    status VARCHAR(30) NOT NULL,
    message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
