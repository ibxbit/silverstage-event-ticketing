CREATE TABLE IF NOT EXISTS event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS season (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_season_event FOREIGN KEY (event_id) REFERENCES event(id)
);

CREATE TABLE IF NOT EXISTS session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    season_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_season FOREIGN KEY (season_id) REFERENCES season(id)
);

CREATE TABLE IF NOT EXISTS stand (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_stand_session FOREIGN KEY (session_id) REFERENCES session(id),
    CONSTRAINT uq_stand_session_code UNIQUE (session_id, code)
);

CREATE TABLE IF NOT EXISTS zone (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stand_id BIGINT,
    session_id BIGINT NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(120) NOT NULL,
    capacity INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_zone_stand FOREIGN KEY (stand_id) REFERENCES stand(id),
    CONSTRAINT fk_zone_session FOREIGN KEY (session_id) REFERENCES session(id),
    CONSTRAINT uq_zone_session_code UNIQUE (session_id, code)
);

CREATE TABLE IF NOT EXISTS seat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    zone_id BIGINT NOT NULL,
    seat_number VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_seat_zone FOREIGN KEY (zone_id) REFERENCES zone(id),
    CONSTRAINT uq_seat_zone_number UNIQUE (zone_id, seat_number)
);

CREATE TABLE IF NOT EXISTS local_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT,
    payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ticket_type (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    base_price DECIMAL(10,2) NOT NULL,
    visibility_scope VARCHAR(30) NOT NULL,
    sale_start DATETIME NOT NULL,
    sale_end DATETIME NOT NULL,
    total_inventory INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_type_event FOREIGN KEY (event_id) REFERENCES event(id),
    CONSTRAINT uq_ticket_type_event_code UNIQUE (event_id, code)
);

CREATE TABLE IF NOT EXISTS ticket_price_tier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_type_id BIGINT NOT NULL,
    tier_order INT NOT NULL,
    min_quantity INT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_tier_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES ticket_type(id),
    CONSTRAINT uq_ticket_tier_unique UNIQUE (ticket_type_id, min_quantity)
);

CREATE TABLE IF NOT EXISTS ticket_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_type_id BIGINT NOT NULL,
    channel VARCHAR(30) NOT NULL,
    allocated INT NOT NULL,
    sold INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_inventory_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES ticket_type(id),
    CONSTRAINT uq_ticket_inventory_channel UNIQUE (ticket_type_id, channel)
);

CREATE TABLE IF NOT EXISTS ticket_reservation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_type_id BIGINT NOT NULL,
    reservation_code VARCHAR(60) NOT NULL,
    buyer_reference VARCHAR(80) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_reservation_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES ticket_type(id),
    CONSTRAINT uq_ticket_reservation_code UNIQUE (reservation_code)
);

CREATE TABLE IF NOT EXISTS ticket_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    ticket_type_id BIGINT NOT NULL,
    order_code VARCHAR(60) NOT NULL,
    buyer_reference VARCHAR(80) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    hold_expires_at DATETIME NOT NULL,
    cancel_expires_at DATETIME NOT NULL,
    inventory_returned BIT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_order_event FOREIGN KEY (event_id) REFERENCES event(id),
    CONSTRAINT fk_ticket_order_session FOREIGN KEY (session_id) REFERENCES session(id),
    CONSTRAINT fk_ticket_order_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES ticket_type(id),
    CONSTRAINT uq_ticket_order_code UNIQUE (order_code)
);

CREATE TABLE IF NOT EXISTS ticket_order_seat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_order_seat_order FOREIGN KEY (order_id) REFERENCES ticket_order(id),
    CONSTRAINT fk_ticket_order_seat_seat FOREIGN KEY (seat_id) REFERENCES seat(id)
);

CREATE TABLE IF NOT EXISTS community_announcement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(220) NOT NULL,
    body TEXT NOT NULL,
    author VARCHAR(120) NOT NULL,
    category VARCHAR(80) NOT NULL,
    word_count INT NOT NULL,
    popularity INT NOT NULL DEFAULT 0,
    published_at DATETIME NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document_folder (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    folder_path VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS managed_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    folder_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    access_level VARCHAR(30) NOT NULL DEFAULT 'STAFF_AND_ADMIN',
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_managed_document_folder FOREIGN KEY (folder_id) REFERENCES document_folder(id)
);

CREATE TABLE IF NOT EXISTS managed_document_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    stored_path VARCHAR(500) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    checksum VARCHAR(128) NOT NULL,
    uploaded_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_managed_doc_version_document FOREIGN KEY (document_id) REFERENCES managed_document(id),
    CONSTRAINT uq_doc_version UNIQUE (document_id, version_number)
);

CREATE TABLE IF NOT EXISTS managed_document_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    tag VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_managed_doc_tag_document FOREIGN KEY (document_id) REFERENCES managed_document(id),
    CONSTRAINT uq_doc_tag UNIQUE (document_id, tag)
);

CREATE TABLE IF NOT EXISTS managed_download_link (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    token VARCHAR(120) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_managed_download_document FOREIGN KEY (document_id) REFERENCES managed_document(id),
    CONSTRAINT fk_managed_download_version FOREIGN KEY (version_id) REFERENCES managed_document_version(id)
);

CREATE TABLE IF NOT EXISTS content_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_user VARCHAR(120) NOT NULL,
    reported_user VARCHAR(120) NOT NULL,
    content_type VARCHAR(60),
    content_ref VARCHAR(180),
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    moderator_user VARCHAR(120),
    decision_notes TEXT,
    penalty_type VARCHAR(40),
    penalty_ends_at DATETIME,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME
);

CREATE TABLE IF NOT EXISTS report_evidence (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    stored_path VARCHAR(500) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_evidence_report FOREIGN KEY (report_id) REFERENCES content_report(id)
);

CREATE TABLE IF NOT EXISTS user_penalty (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    username VARCHAR(120) NOT NULL,
    penalty_type VARCHAR(40) NOT NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME,
    active CHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_penalty_report FOREIGN KEY (report_id) REFERENCES content_report(id)
);

CREATE TABLE IF NOT EXISTS user_notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(120) NOT NULL,
    message TEXT NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    read_flag CHAR(1) NOT NULL DEFAULT 'N',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS published_content (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    state VARCHAR(20) NOT NULL,
    current_version INT NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    published_at DATETIME,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS content_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    changed_by VARCHAR(120) NOT NULL,
    change_type VARCHAR(40) NOT NULL,
    change_summary VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_content_version_content FOREIGN KEY (content_id) REFERENCES published_content(id),
    CONSTRAINT uq_content_version UNIQUE (content_id, version_number)
);

CREATE TABLE IF NOT EXISTS content_appeal (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_id BIGINT NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    justification TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    reviewed_by VARCHAR(120),
    review_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME,
    CONSTRAINT fk_content_appeal_content FOREIGN KEY (content_id) REFERENCES published_content(id)
);

CREATE TABLE IF NOT EXISTS content_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_id BIGINT NOT NULL,
    version_id BIGINT,
    action VARCHAR(50) NOT NULL,
    changed_by VARCHAR(120) NOT NULL,
    change_detail TEXT,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_content_audit_content FOREIGN KEY (content_id) REFERENCES published_content(id),
    CONSTRAINT fk_content_audit_version FOREIGN KEY (version_id) REFERENCES content_version(id)
);

CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    lockout_until DATETIME,
    active CHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(120) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE IF NOT EXISTS user_identity_verification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    full_name_encrypted TEXT NOT NULL,
    id_type VARCHAR(40) NOT NULL,
    id_number_encrypted TEXT NOT NULL,
    id_number_masked VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reviewed_by VARCHAR(80),
    review_notes TEXT,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at DATETIME,
    CONSTRAINT fk_user_verification_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE IF NOT EXISTS payment_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_ref VARCHAR(120) NOT NULL UNIQUE,
    tender_type VARCHAR(30) NOT NULL,
    gross_amount DECIMAL(12,2) NOT NULL,
    refunded_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    merchant_code VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS settlement_callback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_ref VARCHAR(120) NOT NULL UNIQUE,
    gateway_batch_ref VARCHAR(120),
    settled_amount DECIMAL(12,2) NOT NULL,
    callback_status VARCHAR(20) NOT NULL,
    source VARCHAR(40) NOT NULL,
    callback_at DATETIME NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS refund_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    transaction_ref VARCHAR(120) NOT NULL,
    refund_amount DECIMAL(12,2) NOT NULL,
    refund_type VARCHAR(20) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payment_transaction(id)
);

CREATE TABLE IF NOT EXISTS revenue_share_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_ref VARCHAR(120) NOT NULL,
    platform_share DECIMAL(12,2) NOT NULL,
    merchant_share DECIMAL(12,2) NOT NULL,
    merchant_code VARCHAR(80) NOT NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reconciliation_exception (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_ref VARCHAR(120) NOT NULL,
    exception_type VARCHAR(40) NOT NULL,
    detail VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS operation_trace (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id VARCHAR(80) NOT NULL,
    actor VARCHAR(80) NOT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_ref VARCHAR(120),
    payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
