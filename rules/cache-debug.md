# Rule: Debug Hazelcast cache qua Management Center

> Áp dụng khi consumer dùng starter `security-core` và cần **xem data đang nằm trong Hazelcast cache** để debug (vd: verify permission có cập nhật đúng sau khi admin sửa role, kiểm tra entry stale, đo cache hit rate).
>
> Hazelcast là **cache RAM**, KHÔNG phải database. MC chỉ cho thấy những gì đã được code load — không phải toàn bộ data nghiệp vụ. Muốn xem full role/permission/menu → query PostgreSQL hoặc Swagger.

> 📝 **Changelog rule này** (bám theo behavior thực tế của starter):
> - **2026-05-28** — Sửa SCRIPT 1 cho đúng type `Collection<String>` của `userAuthoritiesByUsername` (trước đó script gọi `user.getAuthorities()` sai type, throw `TypeError`). Bug starter [#S009](../../../../../../../Bug/BUGS-starter.md).
> - **2026-05-28** — Sửa format key cache `sec-permission-matrix` từ `[ROLE_A, ROLE_B]` (TreeSet.toString) sang `ROLE_A|ROLE_B` (pipe-join) cho khớp `RequestPermissionSnapshot.toCacheKey`. Bug starter [#S010](../../../../../../../Bug/BUGS-starter.md).
> - **2026-05-29** — Bỏ TTL khỏi `userAuthoritiesByUsername` (60s → ∞) và `sec-permission-matrix` (3600s → ∞). Thay `@CacheEvict(allEntries=true)` bằng `UserAuthorityCacheService.evictByAuthority(name)` để chỉ evict user / matrix entry liên quan đến role bị sửa. Consumer cần gọi `evictAll()` thủ công sau migration SQL trực tiếp vì TTL safety net không còn. Áp dụng từ starter v0.1.1.

---

## TL;DR

| Mục tiêu | Tool đúng |
|---|---|
| Xem toàn bộ role/permission/menu (data nghiệp vụ) | PostgreSQL (DBeaver/psql) hoặc Swagger UI |
| Xem entry đang trong RAM cache (debug stale/invalidation) | MC Scripting hoặc Map Browser |
| Verify `@CacheEvict` chạy đúng | MC Scripting → dump trước/sau update |

---

## 1. Map names + key/value format

Cache thực tế starter populate vào Hazelcast:

| Map name | Key | Value | Populate khi | Evict khi |
|---|---|---|---|---|
| `userAuthoritiesByUsername` | `String username` (vd `"admin"`) | `Collection<String>` authority names resolved cho user đó | Consumer `CurrentUserAuthorityProvider` hoặc starter `DefaultCurrentUserAuthorityResolver` resolve khi cache miss | (a) Consumer gọi `UserAuthorityCacheService.evict(username)` khi logout / update user-role / disable user; (b) `SecPermissionService.save/update/delete*` gọi `evictByAuthority(authorityName)` — chỉ evict user có authority đó; (c) `SecRoleAdminResource` PUT/DELETE gọi `evictByAuthority(name)`; (d) LRU + USED_HEAP_SIZE khi heap đầy. **KHÔNG có TTL** — entry sống đến khi explicit evict. |
| `sec-permission-matrix` | `String` = `String.join("|", new TreeSet<>(authorities))` (vd `"ROLE_ADMIN|ROLE_USER"`) | `PermissionMatrix` (CRUD/row/attribute) | Request RBAC gọi `getMatrix()` → `cache.computeIfAbsent(key, ...)` | (a) `SecPermissionService.save/update/delete*` gọi `evictByAuthority(authorityName)` — chỉ evict matrix key chứa authority đó; (b) `SecRoleAdminResource` PUT/DELETE gọi `evictByAuthority(name)`; (c) LRU + USED_HEAP_SIZE khi heap đầy. **KHÔNG có TTL.** Logout user KHÔNG touch matrix vì matrix shared theo bộ role. |
| `com.vn.security.core.domain.*` | Entity ID | Entity object | Hibernate L2 cache (load entity qua JPA) | Hibernate tự manage, TTL 3600s |
| `default` | — | — | Fallback, hiếm khi dùng | — |

> ⚠️ **Lịch sử evolution**:
> - v0.0.5: thêm `@CacheEvict(allEntries=true)` trên mọi write-path role/permission + TTL 60s an toàn cho các đường rò rỉ (migration script).
> - v0.1.x (bản hiện tại): **bỏ TTL** trên cả hai cache, thay `allEntries=true` bằng `evictByAuthority(authorityName)` — chỉ xoá user / matrix entry liên quan đến role bị sửa. Cache user X **không** bị xoá khi admin sửa role không liên quan tới X. Trade-off: rủi ro xoá thiếu nếu permission write bypass `SecPermissionService` / `SecRoleAdminResource` (vd migration SQL trực tiếp) → consumer phải gọi `UserAuthorityCacheService.evictByAuthority(name)` hoặc `evictAll()` thủ công sau migration.

> ⚠️ Key của `sec-permission-matrix` **shared theo bộ role**, KHÔNG theo user. 100 user cùng role ⇒ 1 entry cache duy nhất.

> ⚠️ Key dùng pipe-join chứ KHÔNG dùng `TreeSet.toString()`. Lý do: `AbstractCollection.toString()` không phải contract của JDK nên không reliable. Khi script JS build cacheKey để lookup, phải dùng `roleNames.join("|")` — KHÔNG dùng `"[" + roleNames.join(", ") + "]"`. Xem `RequestPermissionSnapshot.toCacheKey()`.

---

## 2. Setup Management Center (chỉ cần làm 1 lần)

### Yêu cầu trên consumer app

1. **`spring.profiles.active: dev`** trong `application.yml`. Nếu không có profile dev → code starter dùng `ManagementCenterConfig` rỗng → MC không browse được entry value.

2. **Nashorn JS engine** (chỉ cần khi muốn dùng MC Scripting). JDK 15+ đã loại bỏ Nashorn → phải add lại:

   ```gradle
   // build.gradle của consumer
   dependencies {
       // dev-only, không deploy production
       developmentOnly 'org.openjdk.nashorn:nashorn-core:15.4'
   }
   ```

   Không cần khi chỉ dùng Map Browser (lookup theo key).

### Chạy MC qua Docker

```powershell
docker run -d --name hz-mc -p 8080:8080 `
    -e MC_DEFAULT_CLUSTER=dev `
    -e MC_DEFAULT_CLUSTER_MEMBERS=host.docker.internal:5701 `
    hazelcast/management-center:5.11
```

- **Version chốt**: `5.11` (latest stable, không có bug JS `Cannot read properties of undefined (reading 'initialize')` của bản `5.5.2`).
- **`host.docker.internal:5701`**: container reach host Windows. KHÔNG dùng `127.0.0.1:5701` (trong container = chính container).
- App starter phải bind `0.0.0.0:5701` (đã set sẵn trong dev profile từ v0.0.4 trở đi).

### Lệnh quản lý

```powershell
docker logs hz-mc --tail 30      # debug khi MC không thấy cluster
docker restart hz-mc             # MC treo
docker stop hz-mc                # dừng tạm
docker start hz-mc               # bật lại
docker rm -f hz-mc               # xoá hẳn (mất admin account)
```

---

## 3. Xem data: 2 cách

### Cách A — Scripting Console (xem TẤT CẢ entries)

Yêu cầu: Nashorn dep đã add (xem §2).

MC sidebar trái → **Cluster → Scripting**:
- Language: **JavaScript**
- Members: **All Members**
- Paste:

```javascript
var map = hazelcast.getMap("userAuthoritiesByUsername");
var keys = map.keySet().toArray();
var out = "Total: " + map.size() + " entries\n\n";
for (var i = 0; i < keys.length; i++) {
  var k = keys[i];
  out += "KEY: " + k + "\n";
  out += "VALUE: " + map.get(k) + "\n\n";
}
out;
```

- **Execute Script** → output liệt kê tất cả entry.

### Cách B — Map Browser (lookup 1 key cụ thể)

Không cần Nashorn. MC → **Storage → Maps → click tên map → Map Browser**:
- Key Type: `String`
- Key: nhập đúng login (vd `admin`)
- **Browse** → ra value

Lookup key sai = "No value found for key". Phải biết key chính xác.

Lấy danh sách login từ DB:

```powershell
docker run --rm -e PGPASSWORD=123456 postgres:17 `
  psql -h host.docker.internal -U postgres -d db_react_springboot `
  -c "SELECT login FROM <consumer_user_table>;"
```

---

## 4. Workflow debug: admin update → cache có đúng không?

### Case A: Admin sửa **permission của role** (vd thêm `READ` cho `ROLE_USER`)

```
Bước 1. User đã login từ trước → sec-permission-matrix có entries.
        Script 1 → ghi nhận N entries.

Bước 2. Admin gọi API update permission (vd POST/PUT /api/admin/permissions).

Bước 3. Chạy lại Script 1 ngay sau update:
        → KỲ VỌNG: "Map size: 0" (đã @CacheEvict allEntries=true sạch).
        → Nếu vẫn có entries cũ ⇒ BUG: @CacheEvict không trigger.

Bước 4. User gọi API tiếp (vd GET /api/organizations).

Bước 5. Chạy lại Script 1:
        → KỲ VỌNG: có entry mới với KEY = bộ role user.
        → Verify VALUE chứa permission mới admin vừa sửa.
```

**Kết luận case A**: user KHÔNG cần logout. JWT chỉ chứa role (vd `ROLE_USER`), không chứa permission. Matrix tự refresh trên request kế tiếp.

### Case B: Admin **gán role mới cho user X** (vd thêm `ROLE_ADMIN`)

```
Bước 1. User X authenticated → Script 2 (dump userAuthoritiesByUsername) → entry "userX" có role cũ.

Bước 2. Admin gọi API update user X.
        → Consumer user service evict userAuthoritiesByUsername[user.login].
        → Script 2: entry "userX" biến mất.

Bước 3. User X (vẫn dùng JWT cũ) gọi API.
        → JWT chứa role cũ (chưa expire), authorities vẫn cũ.
        → sec-permission-matrix lookup theo bộ role cũ → cache hit/miss đều ra permission cũ.

Bước 4. User X PHẢI logout + login lại.
        → JWT mới có role mới.
        → Script 1 (dump sec-permission-matrix): entry mới với KEY là bộ role mới.

Bước 5. Verify VALUE entry mới chứa permission đúng.
```

**Kết luận case B**: chỉ user X bị ảnh hưởng cần logout. User khác có cùng role không cần.

---

## 5. Sample scripts (lưu vào notepad để dùng lại)

> Tất cả script dưới đây **chỉ đọc RAM Hazelcast, KHÔNG call DB**. Hazelcast lưu BINARY → khi `map.get()` deserialize ra object đã detached khỏi Hibernate → mọi method/reflection call lên object đều không trigger DB query.
>
> Mọi nhánh có risk (Hibernate lazy field) đều bọc `try/catch` → script không vỡ giữa chừng.

### SCRIPT 1 — Overview: user + role + matrix CRUD/ATTRIBUTE

Trả lời: ai đang trong cache, role gì, được làm gì.

> ⚠️ Value của `userAuthoritiesByUsername` là `Collection<String>` (authority names đã validate), KHÔNG phải `User` entity. Xem `DefaultCurrentUserAuthorityResolver.java:78` (`IMap<String, Collection<String>>`). KHÔNG gọi `user.getAuthorities()/getFirstName()/...` lên value này — sẽ throw `TypeError: ... is not a function`.

```javascript
var users = hazelcast.getMap("userAuthoritiesByUsername");
var matrix = hazelcast.getMap("sec-permission-matrix");

var out = "";
var userKeys = users.keySet().toArray();
out += "=== USERS IN CACHE (" + userKeys.length + ") ===\n\n";

for (var i = 0; i < userKeys.length; i++) {
  var login = userKeys[i];
  var authorities = users.get(login); // Collection<String>

  // Lấy roles
  var roleNames = [];
  var it = authorities.iterator();
  while (it.hasNext()) {
    roleNames.push(String(it.next()));
  }
  roleNames.sort();

  out += "------------------------------------------\n";
  out += "USER: " + login + "\n";
  out += "  roles: [" + roleNames.join(", ") + "]\n";

  // Key matrix = String.join("|", new TreeSet<>(authorities)) — bộ role pipe-join, đã sort
  var cacheKey = roleNames.join("|");
  var pm = matrix.get(cacheKey);

  if (pm === null) {
    out += "  matrix: <CHƯA POPULATE — user chưa hit API protected>\n\n";
    continue;
  }

  var entities = [], attributes = [];
  try {
    var allowed = pm.getClass().getDeclaredField("allowedKeys");
    allowed.setAccessible(true);
    var keys = allowed.get(pm).toArray();
    for (var j = 0; j < keys.length; j++) {
      var k = keys[j];
      if (k.startsWith("ENTITY:")) entities.push(k.substring(7));
      else if (k.startsWith("ATTRIBUTE:")) attributes.push(k.substring(10));
    }
    entities.sort();
    attributes.sort();
  } catch (e) {
    out += "  <reflection error: " + e.message + ">\n\n";
    continue;
  }

  out += "  CRUD ENTITY (" + entities.length + "):\n";
  for (var j = 0; j < entities.length; j++) {
    out += "    - " + entities[j] + "\n";
  }
  out += "  ATTRIBUTE (" + attributes.length + "):\n";
  for (var j = 0; j < attributes.length; j++) {
    out += "    - " + attributes[j] + "\n";
  }
  out += "\n";
}
out;
```

### SCRIPT 2 — Deep dive: dump FULL field 1 map

Trả lời: 1 map cụ thể có entry gì, mỗi entry có field gì (kể cả audit columns từ superclass).

```javascript
function dump(obj, indent) {
  if (obj === null) return "null";
  var pad = indent || "  ";
  var clazz = obj.getClass();
  if (clazz.isPrimitive() || clazz.getName().startsWith("java.lang.") ||
      clazz.getName().startsWith("java.time.") || clazz.getName() === "java.util.UUID") {
    return String(obj);
  }
  if (java.util.Collection.class.isAssignableFrom(clazz)) {
    try { return "[" + obj + "]"; } catch (e) { return "<collection error>"; }
  }
  var out = clazz.getSimpleName() + " {\n";
  var c = clazz;
  while (c !== null && c.getName() !== "java.lang.Object") {
    var fields = c.getDeclaredFields();
    for (var i = 0; i < fields.length; i++) {
      var f = fields[i];
      if ((f.getModifiers() & 8) !== 0) continue; // skip static
      f.setAccessible(true);
      try {
        var val = f.get(obj);
        out += pad + "[" + c.getSimpleName() + "] " + f.getName() + " = " + val + "\n";
      } catch (e) {
        out += pad + f.getName() + " = <error>\n";
      }
    }
    c = c.getSuperclass();
  }
  out += "}";
  return out;
}

// === MAIN ===
var map = hazelcast.getMap("userAuthoritiesByUsername");  // ← đổi tên map ở đây
var keys = map.keySet().toArray();
var result = "Map: " + map.getName() + " (size=" + map.size() + ")\n\n";
for (var i = 0; i < keys.length; i++) {
  var k = keys[i];
  result += "================\n";
  result += "KEY: " + k + "\n";
  result += "VAL: " + dump(map.get(k)) + "\n\n";
}
result;
```

Đổi tên map ở dòng `var map = hazelcast.getMap("...")` (vd `sec-permission-matrix`, `com.vn.security.core.security.domain.SecPermission`, ...).

⚠️ **Lưu ý bảo mật**: dump mọi field bao gồm `password` (BCrypt hash). Chỉ chạy dev, không paste output lên chỗ public.

### SCRIPT 3 — Khám phá: list TẤT CẢ map có data

Trả lời: tổng quan tất cả map đang tồn tại, key + value tóm tắt.

```javascript
var out = "";
var objects = hazelcast.getDistributedObjects().toArray();
for (var i = 0; i < objects.length; i++) {
  var obj = objects[i];
  if (obj.getServiceName() === "hz:impl:mapService") {
    var name = obj.getName();
    out += "\n========================================\n";
    out += "MAP: " + name + " (size=" + obj.size() + ")\n";
    out += "========================================\n";
    var keys = obj.keySet().toArray();
    for (var j = 0; j < keys.length; j++) {
      var k = keys[j];
      try {
        var v = obj.get(k);
        out += "KEY: " + k + "\n";
        out += "VAL: " + v + "\n";
        if (v && v.getAuthorities) {
          try {
            out += "AUTHORITIES: " + v.getAuthorities() + "\n";
          } catch (e) { out += "AUTHORITIES: <error>\n"; }
        }
        out += "\n";
      } catch (e) {
        out += "KEY: " + k + "\n  <get error: " + e.message + ">\n\n";
      }
    }
  }
}
out;
```

Output có thể bị truncate ở UI MC nếu quá dài (~vài chục KB). Map lớn → filter từng map riêng bằng SCRIPT 2.

### Bonus: clear cache (KHÔNG dùng production)

```javascript
hazelcast.getMap("sec-permission-matrix").clear();
hazelcast.getMap("userAuthoritiesByUsername").clear();
"Cleared";
```

---

## 6. Anti-patterns (đừng làm)

- ❌ **Dùng MC để xem danh sách role/permission/menu** — đó là data DB, không phải cache. MC chỉ thấy những gì code đã load. Dùng DBeaver/psql/Swagger.
- ❌ **Để `setDataAccessEnabled(true)` chạy production** — cho phép đọc/sửa entry value từ MC, leak data nhạy cảm. Starter đã wrap trong `if dev profile`, đừng bỏ guard.
- ❌ **Để Nashorn dep ở `implementation` thay vì `developmentOnly`** — Nashorn nặng (~5MB) và chỉ phục vụ debug. Production jar không cần.
- ❌ **Để `scripting-enabled="true"` + `data-access-enabled="true"` + `console-enabled="true"` ở production** — ai connect MC vào cluster đều có thể chạy script tuỳ ý trên member, tương đương RCE. Starter chỉ bật trong dev profile.
- ❌ **Đoán cache hành xử mà không verify bằng MC** — code có `@CacheEvict` không có nghĩa nó luôn trigger (vd self-invocation không qua proxy). Khi nghi ngờ, dump cache trước/sau bằng Script 1.

---

## 7. Tham chiếu code nguồn

- `com.vn.security.core.config.CacheConfiguration` — Hazelcast config (cluster name, MC permissions, map config).
- `com.vn.security.core.security.permission.RequestPermissionSnapshot` — populate `sec-permission-matrix`.
- Consumer `CurrentUserAuthorityProvider` / user-role service — populate `userAuthoritiesByUsername` nếu dùng cache convention này.
- Consumer user service — evict `userAuthoritiesByUsername` khi update user.
- `com.vn.security.core.service.security.SecPermissionService` — evict `sec-permission-matrix` khi sửa permission.
