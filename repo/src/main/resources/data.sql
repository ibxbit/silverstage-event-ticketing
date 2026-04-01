INSERT INTO event (id, code, name, start_date, end_date)
VALUES (1, 'SENIOR-ARTS-2026', 'Senior Arts Festival', '2026-04-01', '2026-06-30');

INSERT INTO season (id, event_id, name, start_date, end_date)
VALUES
    (1, 1, 'Spring Programs', '2026-04-01', '2026-04-30'),
    (2, 1, 'Summer Programs', '2026-05-01', '2026-06-30');

INSERT INTO session (id, season_id, title, start_time, end_time)
VALUES
    (1, 1, 'Choir Opening Night', '2026-04-07 18:00:00', '2026-04-07 20:00:00'),
    (2, 2, 'Golden Classics Matinee', '2026-05-14 14:00:00', '2026-05-14 16:00:00');

INSERT INTO stand (id, session_id, code, name)
VALUES
    (1, 1, 'WEST', 'West Stand'),
    (2, 1, 'EAST', 'East Stand'),
    (3, 2, 'CENTER', 'Center Stand');

INSERT INTO zone (id, stand_id, session_id, code, name, capacity)
VALUES
    (1, 1, 1, 'FRONT', 'Front Orchestra', 80),
    (2, 2, 1, 'BAL', 'Balcony', 40),
    (3, 3, 2, 'MAIN', 'Main Hall', 90);

INSERT INTO seat (id, zone_id, seat_number, status)
VALUES
    (1, 1, 'A01', 'AVAILABLE'),
    (2, 1, 'A02', 'RESERVED'),
    (3, 2, 'B01', 'AVAILABLE'),
    (4, 3, 'C01', 'AVAILABLE');

INSERT INTO ticket_type (
    id, event_id, code, name, base_price, visibility_scope, sale_start, sale_end, total_inventory
)
VALUES
    (1, 1, 'GENERAL_ADMISSION', 'General Admission', 45.00, 'PUBLIC', '2026-03-25 09:00:00', '2026-04-10 17:00:00', 300),
    (2, 1, 'SENIOR_DISCOUNT', 'Senior Discount', 30.00, 'SENIOR_ONLY', '2026-03-25 09:00:00', '2026-04-10 17:00:00', 180),
    (3, 1, 'COMPANION_PASS', 'Companion Pass', 20.00, 'COMPANION_ONLY', '2026-03-25 09:00:00', '2026-04-10 17:00:00', 120);

INSERT INTO ticket_price_tier (id, ticket_type_id, tier_order, min_quantity, price)
VALUES
    (1, 1, 1, 1, 45.00),
    (2, 1, 2, 5, 42.00),
    (3, 1, 3, 10, 39.00),
    (4, 2, 1, 1, 30.00),
    (5, 2, 2, 5, 27.00),
    (6, 3, 1, 1, 20.00),
    (7, 3, 2, 5, 18.00);

INSERT INTO ticket_inventory (id, ticket_type_id, channel, allocated, sold)
VALUES
    (1, 1, 'ONLINE_PORTAL', 180, 0),
    (2, 1, 'BOX_OFFICE', 120, 0),
    (3, 2, 'ONLINE_PORTAL', 108, 0),
    (4, 2, 'BOX_OFFICE', 72, 0),
    (5, 3, 'ONLINE_PORTAL', 72, 0),
    (6, 3, 'BOX_OFFICE', 48, 0);

INSERT INTO community_announcement (id, title, body, author, category, word_count, popularity, published_at)
VALUES
    (
        1000,
        'Neighborhood Choir Registration Extended',
        'The neighborhood choir registration has been extended by one week. Seniors and family companions can complete forms at the front desk between 9 AM and 4 PM.',
        'Maria Santos',
        'Programs',
        28,
        57,
        '2026-03-20 10:30:00'
    ),
    (
        2,
        'Volunteer Ushers Needed for Spring Sessions',
        'Our spring sessions need volunteer ushers to support mobility assistance and guided seating. Orientation takes place this Saturday in the community hall.',
        'Daniel Brooks',
        'Volunteer',
        23,
        35,
        '2026-03-22 08:15:00'
    ),
    (
        3,
        'Health and Wellness Talk Added to Summer Programs',
        'A new health and wellness talk has been added to summer programs. Topics include nutrition, safe stretching, and social wellness for active aging.',
        'Maria Santos',
        'Wellness',
        24,
        42,
        '2026-03-24 14:45:00'
    );

INSERT INTO document_folder (id, folder_path)
VALUES
    (1, '/waivers'),
    (2, '/accessibility'),
    (3, '/flyers');

INSERT INTO published_content (id, title, body, state, current_version, created_by, published_at)
VALUES
    (
        1000,
        'Spring Choir Update Bulletin',
        'Draft bulletin for spring choir updates and weekly rehearsal reminders.',
        'DRAFT',
        1,
        'author_1',
        NULL
    );

INSERT INTO content_version (id, content_id, version_number, title, body, changed_by, change_type, change_summary)
VALUES
    (
        1000,
        1000,
        1,
        'Spring Choir Update Bulletin',
        'Draft bulletin for spring choir updates and weekly rehearsal reminders.',
        'author_1',
        'CREATE',
        'Initial draft created'
    );

INSERT INTO content_audit_log (id, content_id, version_id, action, changed_by, change_detail)
VALUES
    (1000, 1000, 1000, 'CREATE_DRAFT', 'author_1', 'Initial draft created');
