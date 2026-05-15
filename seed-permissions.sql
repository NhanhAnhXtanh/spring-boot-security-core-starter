-- Cấp full quyền READ/CREATE/UPDATE/DELETE cho ROLE_ADMIN trên 3 secured entities
INSERT INTO sec_permission (id, authority_name, action, target, target_type, effect)
VALUES
  (1, 'ROLE_ADMIN', 'READ',   'com.vn.security.core.domain.Organization', 'ENTITY', 'ALLOW'),
  (2, 'ROLE_ADMIN', 'CREATE', 'com.vn.security.core.domain.Organization', 'ENTITY', 'ALLOW'),
  (3, 'ROLE_ADMIN', 'UPDATE', 'com.vn.security.core.domain.Organization', 'ENTITY', 'ALLOW'),
  (4, 'ROLE_ADMIN', 'DELETE', 'com.vn.security.core.domain.Organization', 'ENTITY', 'ALLOW'),
  (5, 'ROLE_ADMIN', 'READ',   'com.vn.security.core.domain.Department',   'ENTITY', 'ALLOW'),
  (6, 'ROLE_ADMIN', 'CREATE', 'com.vn.security.core.domain.Department',   'ENTITY', 'ALLOW'),
  (7, 'ROLE_ADMIN', 'UPDATE', 'com.vn.security.core.domain.Department',   'ENTITY', 'ALLOW'),
  (8, 'ROLE_ADMIN', 'DELETE', 'com.vn.security.core.domain.Department',   'ENTITY', 'ALLOW'),
  (9, 'ROLE_ADMIN', 'READ',   'com.vn.security.core.domain.Employee',     'ENTITY', 'ALLOW'),
  (10,'ROLE_ADMIN', 'CREATE', 'com.vn.security.core.domain.Employee',     'ENTITY', 'ALLOW'),
  (11,'ROLE_ADMIN', 'UPDATE', 'com.vn.security.core.domain.Employee',     'ENTITY', 'ALLOW'),
  (12,'ROLE_ADMIN', 'DELETE', 'com.vn.security.core.domain.Employee',     'ENTITY', 'ALLOW'),
  -- ROLE_USER chỉ có READ
  (13,'ROLE_USER',  'READ',   'com.vn.security.core.domain.Organization', 'ENTITY', 'ALLOW'),
  (14,'ROLE_USER',  'READ',   'com.vn.security.core.domain.Department',   'ENTITY', 'ALLOW'),
  (15,'ROLE_USER',  'READ',   'com.vn.security.core.domain.Employee',     'ENTITY', 'ALLOW')
ON CONFLICT (id) DO NOTHING;

SELECT authority_name, action, target FROM sec_permission ORDER BY id;
