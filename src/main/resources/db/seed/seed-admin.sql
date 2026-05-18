-- Seed admin/user data cho security-core (vì Liquibase đang disabled).
-- Email column đã bỏ khỏi sec_user (bug #001).
INSERT INTO sec_authority (name, type) VALUES ('ROLE_ADMIN', 'RESOURCE'), ('ROLE_USER', 'RESOURCE')
  ON CONFLICT (name) DO NOTHING;

INSERT INTO sec_user (id, created_by, created_date, activated, login, password_hash, first_name, last_name, lang_key)
VALUES
  (1, 'system', now(), true, 'admin', '$2a$10$gSAhZrxMllrbgj/kkK9UceBPpChGWJA7SYIb1Mqo.n5aNLq1/oRrC', 'Administrator', 'Administrator', 'vi'),
  (2, 'system', now(), true, 'user',  '$2a$10$VEjxo0jq2YG9Rbk2HmX9S.k1uZBGYUHdUcid3g/vfiEl7lwWgOH/K', 'User', 'User', 'vi')
  ON CONFLICT (id) DO NOTHING;

INSERT INTO sec_user_authority (user_id, authority_name) VALUES
  (1, 'ROLE_ADMIN'), (1, 'ROLE_USER'), (2, 'ROLE_USER')
  ON CONFLICT DO NOTHING;

SELECT 'users:' AS label, count(*) FROM sec_user
UNION ALL SELECT 'authorities:', count(*) FROM sec_authority
UNION ALL SELECT 'user_authorities:', count(*) FROM sec_user_authority;
