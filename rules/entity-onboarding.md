# Rule: Thêm hoặc Refactor Entity trong consumer dùng `security-core` starter

> Bổ sung cho [`data-access.md`](data-access.md). Đọc data-access trước.

---

## Nguyên tắc nền (đọc lại trước khi thêm entity)

- **Persistence layer cho entity nghiệp vụ = `EntityManager` của starter** (gián tiếp qua `SecureDataManager` / `UnconstrainedDataManager`).
- **`JpaRepository` đã đóng tập cho entity nghiệp vụ** — starter chỉ sở hữu repository hạ tầng RBAC như `AuthorityRepository`. Consumer được tạo repository cho user/identity store của họ khi implement `SecurityIdentityService`, nhưng mọi entity nghiệp vụ mới phải đi đường EntityManager.
- Marker để vào hệ thống quyền = annotation **`@SecuredEntity`** trên class JPA.

---

## A. Quy trình thêm 1 entity mới trong consumer

Làm theo đúng 6 bước. Bỏ bước nào cũng vỡ một phần (entity không CRUD được, không có quyền, hoặc bị deny ngầm).

### Bước 1 — Tạo class entity JPA + annotate `@SecuredEntity`

```java
package com.acme.app.domain;

import com.vn.security.core.security.catalog.SecuredEntity;
import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;

@SecuredEntity(jpqlAllowed = false)        // bắt buộc. jpqlAllowed = true chỉ khi service cần loadByQuery JPQL custom
@Entity
@Table(name = "acme_invoice")              // consumer tự chọn prefix; tránh đụng table starter (sec_*, proof_*)
public class Invoice implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "number", nullable = false, unique = true, length = 50)
    private String number;

    // getters/setters
    // equals dựa trên id, hashCode dựa trên class — copy mẫu từ ProofDepartment.java
}
```

**Về superclass (tuỳ chọn — starter không ép buộc):**

- Nếu consumer **đã có `BaseEntity` riêng** (audit, soft-delete, multi-tenant, ID strategy chung, …) → **giữ nguyên**, `extends YourBaseEntity`. KHÔNG cần đổi sang `AbstractAuditingEntity`. Starter chỉ yêu cầu `@SecuredEntity`, không quan tâm class cha.
- Nếu consumer **chưa có** BaseEntity và muốn dùng luôn audit fields có sẵn → `extends AbstractAuditingEntity<Long>` của starter.
- Không cần audit, không có BaseEntity → giữ `implements Serializable` như mẫu trên.

Xem [`data-access.md` §3.2](data-access.md) để biết chi tiết 3 lựa chọn.

### Bước 2 — Đảm bảo entity được JPA scan

Starter chỉ scan `com.vn.security.core.domain` và `com.vn.security.core.security.domain`. Entity của consumer (ví dụ `com.acme.app.domain`) sẽ **không bị scan** nếu consumer không khai bổ sung.

Trong main `@SpringBootApplication` của consumer:

```java
@SpringBootApplication
@EntityScan(basePackages = {
    "com.vn.security.core.domain",         // entity của starter — bắt buộc giữ
    "com.vn.security.core.security.domain",
    "com.acme.app.domain"                  // entity của consumer
})
public class Application {
    public static void main(String[] args) { SpringApplication.run(Application.class, args); }
}
```

Thiếu bước này → `MetamodelSecuredEntityCatalog` không thấy entity, `SecureDataManager` reject với "entity not registered".

### Bước 3 — Migration tạo bảng (Liquibase)

Consumer dùng Liquibase changelog riêng, include vào `db/changelog/master.xml`. Đảm bảo schema khớp `@Column` đã khai trong entity.

### Bước 4 — Seed permission

Mỗi entity cần record trong `sec_permission`. Tạo file `db/seed/seed-invoice-permissions.sql`:

```sql
INSERT INTO sec_permission (id, authority_name, action, target, target_type, effect)
VALUES
  (1001, 'ROLE_ADMIN', 'READ',   'com.acme.app.domain.Invoice', 'ENTITY', 'ALLOW'),
  (1002, 'ROLE_ADMIN', 'CREATE', 'com.acme.app.domain.Invoice', 'ENTITY', 'ALLOW'),
  (1003, 'ROLE_ADMIN', 'UPDATE', 'com.acme.app.domain.Invoice', 'ENTITY', 'ALLOW'),
  (1004, 'ROLE_ADMIN', 'DELETE', 'com.acme.app.domain.Invoice', 'ENTITY', 'ALLOW'),
  (1005, 'ROLE_USER',  'READ',   'com.acme.app.domain.Invoice', 'ENTITY', 'ALLOW')
ON CONFLICT (id) DO NOTHING;
```

Reference từ Liquibase changelog:
```xml
<changeSet id="seed-invoice-permissions" author="acme">
    <sqlFile path="db/seed/seed-invoice-permissions.sql" relativeToChangelogFile="false"/>
</changeSet>
```

Quy ước:
- `target` = **FQCN** của entity (không phải table name, không phải code).
- `target_type = 'ENTITY'`.
- ID dùng dải riêng của consumer (ví dụ ≥ 1000) để tránh đụng seed của starter (ID 1–15).

### Bước 5 — Khai fetch plans

Mặc định `@SecuredEntity` tự sinh 2 code: `invoice-list` và `invoice-detail`. Phải có entry tương ứng trong `fetch-plans.yml` (file consumer chỉ đến qua `security-core.fetch-plans.config`):

```yaml
fetch-plans:
  - entity: com.acme.app.domain.Invoice
    name: invoice-list
    properties:
      - id
      - number
      - issuedDate

  - entity: com.acme.app.domain.Invoice
    name: invoice-detail
    extends: invoice-list
    properties:
      - totalAmount
      - name: customer
        properties:
          - id
          - name
```

Nếu muốn fetch plan khác tên → đổi `fetchPlanCodes` trong annotation `@SecuredEntity`.

### Bước 6 — Service + REST resource

Service: copy nguyên pattern từ `ProofDepartmentService` (xem [`data-access.md`](data-access.md) §1). Không tạo `InvoiceRepository extends JpaRepository`. Chỉ inject `SecureDataManager` (+ `EntityManager` nếu cần resolve reference).

```java
@Service
@Transactional
public class InvoiceService {

    private static final Class<Invoice> ENTITY_CLASS = Invoice.class;

    private final SecureDataManager secureDataManager;
    private final EntityManager entityManager;

    public InvoiceService(SecureDataManager secureDataManager, EntityManager entityManager) {
        this.secureDataManager = secureDataManager;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Page<Invoice> list(Pageable pageable) {
        return secureDataManager.loadList(ENTITY_CLASS, pageable);
    }

    public Invoice create(EntityMutation<Invoice> mutation) {
        return secureDataManager.save(ENTITY_CLASS, null, mutation);
    }

    public Invoice update(Long id, EntityMutation<Invoice> mutation) {
        return secureDataManager.save(ENTITY_CLASS, id, mutation);
    }

    public void delete(Long id) {
        secureDataManager.delete(ENTITY_CLASS, id);
    }
}
```

REST resource: copy theo `ProofDepartmentResource`.

### Smoke test 1 phút sau khi xong 6 bước

```bash
# login admin → lấy JWT
curl -X POST http://localhost:8080/api/authenticate \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}'

# list invoice (kỳ vọng 200, page rỗng)
curl http://localhost:8080/api/invoices -H "Authorization: Bearer $JWT"

# login ROLE_USER → kỳ vọng list được (READ allow), create bị 403 (chưa cấp CREATE)
```

Nếu 403 mọi nơi → check Bước 4 (seed permission). Nếu "entity not registered" → check Bước 2 (`@EntityScan`).

---

## B. Refactor entity cũ trong consumer sang pattern này

Áp dụng khi consumer đã có entity X dùng `JpaRepository<X, ?>` từ trước và muốn chuyển sang quản lý qua `SecureDataManager`.

### Thứ tự bắt buộc (đừng đảo bước)

1. **Add `@SecuredEntity` lên class entity.** Đây là yêu cầu **duy nhất** ở mức class. Superclass giữ nguyên theo hiện trạng consumer:
   - Đã có `BaseEntity` riêng → giữ. KHÔNG ép sang `AbstractAuditingEntity`.
   - Chưa có và muốn audit → có thể extend `AbstractAuditingEntity<ID>`. Khi đó schema cần thêm 4 cột audit; viết migration Liquibase add cột với default sensible (ví dụ `created_by = 'system'`, `created_date = now()`).
   - Không cần audit, không có BaseEntity → `implements Serializable` là đủ.
2. **Bổ sung `@EntityScan`** nếu entity nằm ngoài package starter (thường đã có sẵn từ trước).
3. **Seed permission** cho entity (Bước 4 ở mục A). Không bỏ — nếu thiếu, user nào cũng bị 403 sau khi switch.
4. **Khai fetch plans** (Bước 5 ở mục A).
5. **Refactor service**: thay mọi call `xRepository.findAll/findById/save/delete` bằng `secureDataManager.loadList/loadOne/save/delete`. Save phải bọc `EntityMutation`.
6. **Refactor controller/resource** nếu nó đang inject `xRepository` thẳng (anti-pattern) — di chuyển logic xuống service.
7. **Xóa `XRepository.java`** (file `interface XRepository extends JpaRepository<...>`). Để tồn tại = mời người khác inject lại = vô hiệu hoá rule.
8. **Chạy test + smoke test** như mục A.

### Bẫy hay gặp khi refactor

| Triệu chứng | Nguyên nhân | Cách sửa |
|---|---|---|
| `AccessDeniedException` ngay cả với admin | Quên seed permission cho FQCN của entity | Bước 3 |
| `Entity not registered in catalog` | Thiếu `@SecuredEntity` hoặc thiếu `@EntityScan` cho package | Bước 1 + 2 |
| Save thành công nhưng cột nào cũng update được, attribute-level check bị bỏ qua | Không truyền `changedAttributes` trong `EntityMutation` | Refactor service: lấy danh sách attr thật sự thay đổi từ request body |
| Fetch plan code không tồn tại | Quên thêm vào `fetch-plans.yml` hoặc đặt sai `code` | Bước 4 / chỉnh `code` trong `@SecuredEntity` |
| Cũ vẫn dùng `xRepository` đâu đó | Search chưa kỹ | `grep -rn "XRepository" src/` rồi xóa hết |

---

## C. Checklist tổng kết (review PR thêm/refactor entity)

- [ ] Entity có `@SecuredEntity` (+ `code`/`jpqlAllowed` đúng nhu cầu)?
- [ ] Package entity nằm trong `@EntityScan` của consumer?
- [ ] Có migration tạo bảng (và migration thêm cột audit nếu extend `AbstractAuditingEntity` — không bắt buộc nếu consumer dùng BaseEntity riêng)?
- [ ] Có seed permission đầy đủ `READ/CREATE/UPDATE/DELETE` cho role cần thiết?
- [ ] Có fetch plan `{code}-list` và `{code}-detail` (hoặc custom codes khớp annotation)?
- [ ] Service dùng `SecureDataManager`, không tạo `JpaRepository` mới?
- [ ] Save dùng `EntityMutation` với `changedAttributes` đúng từ body request?
- [ ] Reference field được resolve qua `secureDataManager.loadOne` trước, rồi mới `entityManager.find`?
- [ ] (Refactor) Đã xóa file `XRepository.java` cũ?

---

## D. Tham chiếu code mẫu

- Entity gốc (demo proof entities — kèm prefix `Proof` để consumer không bị đụng tên): `com.vn.security.core.domain.ProofDepartment` / `ProofEmployee` / `ProofOrganization` (không extend AbstractAuditing) — pattern phổ biến.
- Base user tùy chọn: `com.vn.security.core.domain.SecurityUser<ID>` (mapped superclass, implements Spring Security `UserDetails`; consumer tự tạo entity/table/id).
- Service: `com.vn.security.core.service.ProofDepartmentService`.
- REST: `com.vn.security.core.web.rest.ProofDepartmentResource`.
- Catalog scanner: `com.vn.security.core.security.catalog.MetamodelSecuredEntityCatalog`.
- Seed permission mẫu: `src/main/resources/db/seed/seed-permissions.sql`.
- Fetch plan mẫu: `src/main/resources/fetch-plans.yml`.

---

## E. Pitfalls phát hiện trong các lần tích hợp thực tế (2026-05-18)

### E.1. ATTRIBUTE permission KHÔNG hỗ trợ wildcard `*` global

Bước 4 nói seed `target=FQCN, target_type='ENTITY'`. Nhưng `EntityMutation.changedAttributes` (luôn cần khi `secureDataManager.save`) còn check **ATTRIBUTE-level permission** trước khi merge field.

Format khác hẳn ENTITY:

| target_type | target format | Hỗ trợ wildcard global `*`? |
|---|---|---|
| ENTITY | FQCN hoặc `*` | ✓ |
| ATTRIBUTE | `<ENTITY_NAME_UPPERCASE>.<FIELD>` hoặc `<ENTITY_NAME>.*` | ✗ — phải seed per-entity |

Action enum: `VIEW`, `EDIT` (không phải READ/CREATE/UPDATE/DELETE).

Thiếu → POST/PUT 500 `No EDIT permission for X.field`. GET vẫn 200.

```sql
INSERT INTO sec_permission (id, authority_name, action, target, target_type, effect) VALUES
  (1006, 'ROLE_ADMIN', 'VIEW', 'INVOICE.*', 'ATTRIBUTE', 'ALLOW'),
  (1007, 'ROLE_ADMIN', 'EDIT', 'INVOICE.*', 'ATTRIBUTE', 'ALLOW'),
  (1008, 'ROLE_USER',  'VIEW', 'INVOICE.*', 'ATTRIBUTE', 'ALLOW')
ON CONFLICT (id) DO NOTHING;
```

### E.2. Fetch plan TUỲ CHỌN trừ khi service request fetchPlanCode

Bước 5 nói "Phải có entry trong `fetch-plans.yml`". Chính xác:

- **BẮT BUỘC**: service gọi `SecuredLoadQuery.builder().fetchPlanCode("...")`, hoặc dùng deprecated Map-returning methods, hoặc dùng `SecureEntitySerializer`.
- **TUỲ CHỌN**: service chỉ dùng typed methods (`loadList/loadOne/save/delete`) hoặc `loadByQuery` không pass `fetchPlanCode` → thiếu entry KHÔNG fail app, JPA dùng fetchgraph mặc định.

**Cảnh báo shadowing**: `security-core.fetch-plans.config: classpath:fetch-plans.yml` mặc định trỏ file consumer. Consumer tạo file này = starter's bundled default bị shadow hoàn toàn → entity demo starter mất fetch plan. Phải copy lại các entry starter vào consumer file nếu test/code gọi tới.

### E.3. `security-core.cache.enabled=false` không thật sự skip cache

Set → trước đây từng boot crash vì user-management mặc định phụ thuộc `CacheManager`. User-management mặc định đã bị gỡ; nếu gặp lỗi cache mới, kiểm tra bean `CacheManager` và cấu hình `security-core.cache.enabled`.

### E.4. `Pageable.unpaged()` → HTTP 500

`secureDataManager.loadList(Class, Pageable.unpaged())` trả 500 `"message":null`. Workaround: `PageRequest.of(0, 10_000)`. Nếu cần unpaged thật → redesign endpoint (luôn paginate).

### E.5. Entity `@OneToOne` cascade với reverse `@ManyToOne` — phải 2-phase save

Pattern: `Parent.currentChild @OneToOne(cascade=ALL)` + `Child.parent @ManyToOne`. Single-pass save fail `TransientPropertyValueException` vì cascade cần parent đã persisted để set child.parent.

→ Thứ tự bắt buộc:
1. Save parent alone (không set currentChild).
2. Save child với parent reference đã managed.
3. Save parent lần 2 set currentChild = saved child.

### E.6. Demo entity starter chiếm namespace nghiệp vụ consumer

Starter ship demo entity (`Organization`, `Department`, `Employee`) với `@Service` + REST endpoint `/api/organizations` etc. Consumer có domain trùng tên → boot crash `ConflictingBeanDefinitionException` + `Ambiguous mapping`.

Workaround consumer (giữ class name + table name nguyên):
- `@Service("appOrganizationService")` — distinct bean name
- `@Entity(name = "AppOrganization")` — distinct JPA entity name
- `@SecuredEntity(code = "app-organization")` — distinct catalog code
- `@RequestMapping("/api/app/organizations")` — distinct URL

_Section E derived from integration session 2026-05-18._
