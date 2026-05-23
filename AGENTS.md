# AGENTS.md — Entry point cho AI assistant

> File này là **điểm vào duy nhất** cho mọi AI assistant (Claude Code, Codex, Cursor, Copilot, Windsurf, …) khi làm việc trong **dự án dùng `security-core` starter**.
> Đọc xong file này, AI bắt buộc đọc tiếp các file trong thư mục `rules/`.

---

## Project context

Đây là **Spring Boot Security Core Starter** — reusable starter cung cấp JWT auth, RBAC, auditing, fetch plans, và security utilities. Consumer chỉ cần add dependency là có sẵn toàn bộ pipeline bảo mật.

Khi một dự án khác (consumer) implement starter này, **mọi data access đều phải tuân thủ rule** trong `rules/`. Đó là lý do file rule tồn tại — để bất kỳ AI hoặc dev nào pick up project consumer cũng biết phải viết code theo pattern nào.

---

## Bắt buộc đọc trước khi viết code

| File | Khi nào áp dụng |
|---|---|
| [`rules/data-access.md`](rules/data-access.md) | **Mọi** thao tác đọc/ghi database. Phân biệt `SecureDataManager` vs `UnconstrainedDataManager`, quy ước `JpaRepository` vs `EntityManager`. |
| [`rules/dynamic-authorization.md`](rules/dynamic-authorization.md) | Khi consumer tự làm login/logout/register/SSO và starter chỉ phân quyền theo username. Chuẩn role động + permission động + menu động. |
| [`rules/dynamic-authorization-plan.md`](rules/dynamic-authorization-plan.md) | Khi triển khai code cho mode username-only authorization. Phase-by-phase plan để gỡ auth khỏi starter. |
| [`rules/entity-onboarding.md`](rules/entity-onboarding.md) | Khi **thêm entity mới** hoặc **refactor entity cũ** trong consumer. 6 bước bắt buộc + checklist review. |
| [`rules/cache-debug.md`](rules/cache-debug.md) | Khi cần **xem data Hazelcast cache** (debug permission stale, verify `@CacheEvict`, kiểm tra invalidation sau khi admin sửa role). Setup MC + script mẫu + workflow debug. |

> Khi có rule mới được thêm, mục này phải cập nhật. AI đọc bảng này để biết những file cần load vào context.

---

## Quy tắc tóm tắt (cheat sheet — chi tiết xem trong `rules/`)

1. **CRUD theo user request** → `SecureDataManager`. Không bypass.
2. **Code hệ thống / bootstrap / job đồng bộ không qua user** → `UnconstrainedDataManager`, kèm comment lý do.
3. **Persistence layer:** `EntityManager` (qua `SecureDataManager`) cho mọi entity nghiệp vụ. `JpaRepository` chỉ là ngoại lệ cho identity store của consumer và repository hạ tầng starter như `AuthorityRepository`.
4. **Identity/authentication:** consumer có thể tự làm toàn bộ login/logout/register/SSO. Khi dùng mode này, starter chỉ đọc `Authentication.getName()` và resolve role qua provider theo username. Xem [`rules/dynamic-authorization.md`](rules/dynamic-authorization.md).
5. **Entity mới:** bắt buộc `@SecuredEntity` + `@EntityScan` package consumer + seed permission + fetch plan. Superclass **tuỳ chọn** — nếu consumer đã có `BaseEntity` riêng thì giữ, KHÔNG ép sang `AbstractAuditingEntity`. Xem [`rules/entity-onboarding.md`](rules/entity-onboarding.md).
6. **Khi nghi ngờ** → chọn `SecureDataManager`. Bypass là lỗi mặc định, không phải shortcut.

---

## Cho người mới tới repo (con người)

- Đọc `README.md` để biết cách cài starter và config tối thiểu.
- Đọc `rules/data-access.md` trước khi viết service / controller mới.
- Tham khảo `src/main/java/com/vn/security/core/service/ProofDepartmentService.java` cho pattern chuẩn.
