# Rule: Data Access trong dự án dùng `security-core` starter

> Áp dụng cho **mọi dự án consumer** import starter `com.vn.security.core:security-core`.
> Đây là rule **bắt buộc** — vi phạm sẽ phá vỡ mô hình kiểm soát quyền (RBAC), audit, và row-level security của starter.

---

## TL;DR cho AI / Dev mới

Khi viết code đụng đến database trong dự án consumer:

| Tình huống | Bắt buộc dùng |
|---|---|
| CRUD do **người dùng cuối** gây ra (REST controller, GraphQL, business service tiếp request) | **`SecureDataManager`** |
| Code **đồng bộ / hệ thống nội bộ**, đã tự enforce access control hoặc không liên quan người dùng (seed, migration helper, scheduled job, bootstrap, row-policy evaluator, infra) | **`UnconstrainedDataManager`** |
| Định nghĩa entity JPA mới | **Annotate `@SecuredEntity`** (bắt buộc) + JPA `@Entity` chuẩn — xem chi tiết trong [`entity-onboarding.md`](entity-onboarding.md) |

**Quy ước về persistence layer:**

- **`JpaRepository` → KHÔNG dùng cho entity nghiệp vụ.** Trong starter chỉ có `UserRepository` và `AuthorityRepository` (entity hạ tầng auth/RBAC) là JpaRepository — đây là quy ước cố định, consumer **không tạo thêm** `JpaRepository` nào khác.
- **`EntityManager` → là kênh chính** cho mọi entity còn lại. Nhưng KHÔNG gọi `entityManager.find/persist/merge/remove` thẳng từ service/controller cho entity nghiệp vụ — đi qua `SecureDataManager` (hoặc `UnconstrainedDataManager` khi thoả điều kiện ở §2).
- `EntityManager` chỉ được phép gọi trực tiếp để **resolve managed reference** sau khi đã verify quyền qua `SecureDataManager.loadOne(...)` — xem mẫu `ProofDepartmentService#adaptOrganizationReference`.

---

## 1. `SecureDataManager` — mặc định cho mọi CRUD nghiệp vụ

Interface: `com.vn.security.core.security.data.SecureDataManager`

### Khi nào dùng

- Mọi luồng có **người dùng** đứng sau request (REST/GraphQL/RPC).
- Mọi service nghiệp vụ load/save/delete entity được liệt kê trong `SecuredEntityCatalog`.
- Khi cần áp dụng đầy đủ: **CRUD permission** + **row-level filter** + **attribute-level check** + **fetch plan** + **audit log**.

### Cách dùng (pattern chuẩn — copy theo `ProofDepartmentService`)

```java
@Service
@Transactional
public class FooService {

    private static final Class<Foo> ENTITY_CLASS = Foo.class;

    private final SecureDataManager secureDataManager;
    private final EntityManager entityManager; // chỉ dùng để resolve reference khi cần

    public FooService(SecureDataManager secureDataManager, EntityManager entityManager) {
        this.secureDataManager = secureDataManager;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Page<Foo> list(Pageable pageable) {
        return secureDataManager.loadList(ENTITY_CLASS, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Foo> findOne(Long id) {
        return secureDataManager.loadOne(ENTITY_CLASS, id);
    }

    public Foo create(EntityMutation<Foo> mutation) {
        return secureDataManager.save(ENTITY_CLASS, null, mutation);
    }

    public Foo update(Long id, EntityMutation<Foo> mutation) {
        return secureDataManager.save(ENTITY_CLASS, id, mutation);
    }

    public void delete(Long id) {
        secureDataManager.delete(ENTITY_CLASS, id);
    }
}
```

### Bắt buộc nhớ

- Save **luôn** đi qua `EntityMutation<E>(entity, changedAttributes)` — `changedAttributes` quyết định attribute-level check sẽ áp dụng lên cột nào.
- Khi adapt reference (`entity.organization = ...`) cần load reference **qua `secureDataManager.loadOne(...)`** trước để xác nhận user có quyền nhìn thấy reference đó. Sau đó mới `entityManager.find(...)` để lấy managed instance gắn vào entity hiện tại. Xem mẫu `ProofDepartmentService#adaptOrganizationReference`.

---

## 2. `UnconstrainedDataManager` — chỉ dùng cho code hệ thống

Interface: `com.vn.security.core.security.data.UnconstrainedDataManager`

> Trusted data access interface that **bypasses security enforcement**.

### Khi nào ĐƯỢC dùng

Chỉ khi **một trong các điều kiện** dưới đây đúng:

1. Code chạy **đồng bộ trong hạ tầng nội bộ**, không bắt nguồn từ request của user (ví dụ: `ApplicationRunner` boot-time seed, scheduled job hệ thống, listener phản ứng với event nội bộ).
2. Code thực hiện **chính việc evaluate row-policy / permission** (không thể tự gọi lại `SecureDataManager` vì sẽ recursion).
3. Code đã **tự enforce access control** ở tầng trên (ví dụ: đã kiểm tra `@PreAuthorize`, đã verify ownership thủ công) và cần một path đọc/ghi nhanh, không qua filter.
4. Migration / data-fix script.

### Khi nào CẤM dùng

- Trong REST controller, GraphQL resolver, hoặc bất kỳ service nghiệp vụ nào nhận input từ user.
- Để "tránh phiền" khi gặp `AccessDeniedException` — nếu user không được phép, đừng bypass; sửa permission config.
- Để load entity rồi return ra ngoài hệ thống — sẽ rò rỉ row mà user không được nhìn thấy.

### API ngắn gọn

```java
unconstrainedDataManager.load(Foo.class, id);
unconstrainedDataManager.loadAll(Foo.class);
unconstrainedDataManager.loadOne(Foo.class, spec);
unconstrainedDataManager.loadList(Foo.class, spec);
unconstrainedDataManager.loadPage(Foo.class, spec, pageable);
unconstrainedDataManager.loadListByJpql(Foo.class, jpql, params, fetchPlan);
unconstrainedDataManager.loadPageByJpql(Foo.class, jpql, params, fetchPlan, pageable);
unconstrainedDataManager.save(entity);
unconstrainedDataManager.delete(entity);
```

**Quy tắc đặt comment:** mỗi chỗ inject `UnconstrainedDataManager` nên có 1 dòng comment ngắn nói **lý do được phép bypass** (ví dụ: `// system bootstrap — runs as superuser, no user context`). Reviewer thấy comment đó để verify nhanh.

---

## 3. Entity — `@SecuredEntity` là bắt buộc, `AbstractAuditingEntity` là tuỳ chọn

Có **2 mức** ràng buộc cho entity:

### 3.1. BẮT BUỘC: annotation `@SecuredEntity`

Để một entity tham gia security enforcement (CRUD/row/attribute), entity đó **phải** có annotation `@SecuredEntity`. Đây là cơ chế opt-in của `SecuredEntityCatalog` — starter scan JPA metamodel và chỉ pick entity có annotation này.

```java
import com.vn.security.core.security.catalog.SecuredEntity;

@SecuredEntity                        // bắt buộc
@Entity
@Table(name = "foo")
public class Foo implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;
    // ...
}
```

Tham số chính của `@SecuredEntity`:
- `code` — entity code (mặc định lowercase simple class name). Dùng cho permission target, fetch plan code.
- `fetchPlanCodes` — danh sách fetch plan code (mặc định `{code}-list`, `{code}-detail`).
- `jpqlAllowed` — `true` nếu muốn cho phép gọi `SecureDataManager.loadByQuery` với JPQL tự định nghĩa; mặc định `false` (deny JPQL).

Cùng với annotation, consumer phải:

1. Đảm bảo entity của họ được JPA scan (xem `entity-onboarding.md` — starter chỉ scan package nội bộ, consumer cần `@EntityScan` bổ sung).
2. Seed permission cho entity (insert vào `sec_permission` với `target = FQCN`).
3. Khai báo fetch plans trong `fetch-plans.yml`.

Chi tiết: [`entity-onboarding.md`](entity-onboarding.md).

### 3.2. TUỲ CHỌN: superclass cho audit / base entity

Chỉ ràng buộc duy nhất ở mức class là `@SecuredEntity`. Việc chọn superclass là **quyền của consumer**:

**Lựa chọn A — Consumer đã có `BaseEntity` riêng (audit, soft-delete, multi-tenancy, ID strategy, …):**

- **Giữ nguyên `BaseEntity` của consumer.** KHÔNG bắt buộc đổi sang `AbstractAuditingEntity` của starter.
- Starter không yêu cầu cột audit cố định — `SecureDataManager` / catalog / fetch plan hoạt động dựa trên `@SecuredEntity` + JPA metamodel, không phụ thuộc class cha.
- Nếu `BaseEntity` của consumer đã tự xử lý audit (qua `@MappedSuperclass` + `@EntityListeners(AuditingEntityListener.class)` hoặc cơ chế khác) → đó là pattern hợp lệ, không cần làm gì thêm.

```java
@SecuredEntity
@Entity
@Table(name = "acme_invoice")
public class Invoice extends com.acme.app.domain.BaseEntity {   // BaseEntity của consumer
    // ...
}
```

**Lựa chọn B — Extend `AbstractAuditingEntity<ID>` của starter:**

Chọn khi consumer **chưa có** BaseEntity và muốn dùng luôn audit fields (`createdBy`, `createdDate`, `lastModifiedBy`, `lastModifiedDate`) có sẵn. Khi extend:

- Phải override `getId()` với kiểu generic đúng.
- KHÔNG định nghĩa lại các cột audit — đã có sẵn ở superclass.
- `AuditingEntityListener` được starter auto-config, không cần khai thêm.

**Lựa chọn C — Không extend gì cả:**

Entity `implements Serializable` thuần, không cần audit. Pattern này dùng phổ biến trong starter (`ProofOrganization`, `ProofDepartment`, `ProofEmployee`).

> **Tóm lại:** Cả 3 lựa chọn đều hợp lệ. Yêu cầu duy nhất là entity phải có annotation `@SecuredEntity`. Starter **không can thiệp** vào việc consumer chọn superclass nào.

> ⚠️ **Quan trọng — đừng nhầm lẫn 2 chuyện:**
>
> **Superclass = metadata columns** (audit / soft-delete / tenant). **Không bypass** `SecureDataManager`.
>
> **Path truy cập DB = `SecureDataManager` (mặc định) hoặc `UnconstrainedDataManager` (code hệ thống)** — quyết định bởi tình huống nghiệp vụ ở §1 & §2, **không phải** bởi việc entity extend class nào.
>
> Dù entity dùng `BaseEntity` riêng, `AbstractAuditingEntity`, hay không extend gì:
> - CRUD theo user request → vẫn phải đi qua `SecureDataManager`.
> - Code hệ thống (bootstrap / migration / job / row-policy evaluator / scheduled task không có user context) → dùng `UnconstrainedDataManager` (kèm comment lý do, xem §2).
>
> Không có cách nào bypass `SecureDataManager` bằng việc đổi superclass. Bypass hợp lệ duy nhất là `UnconstrainedDataManager`, và chỉ trong các trường hợp đã liệt kê ở §2.

### 3.3. KHÔNG được làm

- Không inject custom `EntityManagerFactory` thay thế của starter — sẽ phá hỏng auditing listener, transaction manager, và metamodel-based catalog. Dùng đúng `EntityManager` do starter cấu hình.

---

## 4. Decision flow (cho AI khi viết code mới)

```
Bạn cần đọc/ghi database?
│
├─ Code này có chạy thay mặt cho user request không?
│  ├─ CÓ ─► dùng SecureDataManager. STOP.
│  └─ KHÔNG ─► tiếp tục.
│
├─ Code này có đang implement chính việc kiểm tra quyền không?
│  └─ CÓ ─► dùng UnconstrainedDataManager (recursion guard).
│
├─ Code này là job hệ thống / bootstrap / migration / seed?
│  └─ CÓ ─► dùng UnconstrainedDataManager + comment lý do.
│
└─ Còn lại ─► mặc định dùng SecureDataManager.
   Khi nghi ngờ, chọn Secure. An toàn hơn bypass.
```

---

## 5. Checklist khi review PR consumer

- [ ] Có file nào trong `controller/` hoặc `web/rest/` import `UnconstrainedDataManager` không? Nếu có → reject.
- [ ] Có file `*Repository` nào mới (kiểu `interface FooRepository extends JpaRepository<...>`) cho entity nghiệp vụ không? Nếu có → reject. Chỉ `User` + `Authority` (đã có sẵn trong starter) được phép dùng JpaRepository.
- [ ] Có service mới inject `EntityManager` rồi gọi `find/persist/merge/remove` thẳng cho entity nghiệp vụ không? Nếu có → reject, ép qua `SecureDataManager`. (`EntityManager` chỉ được dùng để resolve managed reference sau khi đã verify quyền.)
- [ ] Entity mới có annotation `@SecuredEntity` chưa? (Bắt buộc để vào catalog. Audit fields là tuỳ chọn — extend `AbstractAuditingEntity<ID>` nếu cần.)
- [ ] Entity mới đã được seed permission trong `sec_permission` chưa?
- [ ] Entity mới có fetch plans (`{code}-list`, `{code}-detail`) trong `fetch-plans.yml` chưa?
- [ ] Mọi inject `UnconstrainedDataManager` có comment lý do không?
- [ ] Save có dùng `EntityMutation` với `changedAttributes` đúng không?

---

## 6. Tham chiếu code nguồn (trong starter)

- `com.vn.security.core.security.data.SecureDataManager`
- `com.vn.security.core.security.data.UnconstrainedDataManager`
- `com.vn.security.core.domain.AbstractAuditingEntity`
- Ví dụ sử dụng: `com.vn.security.core.service.ProofDepartmentService`, `ProofEmployeeService`, `ProofOrganizationService`
