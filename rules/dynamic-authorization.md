# Rule: Username-only auth boundary + dynamic role/permission/menu

> Áp dụng khi consumer tự làm toàn bộ login/logout/register/account/SSO, còn
> `security-core` chỉ làm authorization: RBAC, row-level, attribute-level, menu.

---

## Mục tiêu kiến trúc

Authentication thuộc consumer:

- login/logout/register/account/change-password
- SSO/OAuth2/SAML/session/JWT tùy app
- user table, user-role mapping, group mapping
- tạo `Authentication` trong Spring Security

Authorization thuộc starter:

- đọc username từ `SecurityContextHolder`
- resolve authorities động từ username
- build permission matrix từ `sec_permission`
- enforce CRUD/row/attribute
- build/filter menu động

Boundary giữa 2 bên là **username**:

```text
Consumer auth/SSO -> Authentication.getName() = username
security-core     -> username -> authorities -> permissions -> menus
```

Starter không được phụ thuộc user table, password, register DTO, token issuer, hay SSO provider.

---

## 1. Contract bắt buộc: dynamic authorities by username

Starter cần SPI:

```java
public interface CurrentUserAuthorityProvider {
    Collection<String> getAuthorities(String username);
}
```

Consumer implement bằng user table/SSO/group mapping của họ:

```java
@Service
public class AppCurrentUserAuthorityProvider implements CurrentUserAuthorityProvider {

    @Override
    public Collection<String> getAuthorities(String username) {
        // query app_user/app_user_role, SSO group mapping, LDAP, etc.
        return List.of("ROLE_USER");
    }
}
```

Quy tắc:

- Input là username đã authenticated.
- Output là authority name khớp `sec_authority.name`.
- Không return permission trực tiếp ở đây; chỉ return role/authority.
- Nếu user bị disabled/revoked, consumer auth layer nên chặn trước. Nếu muốn chặn ở starter, provider có thể throw `AccessDeniedException` hoặc return empty.

Fallback:

- Nếu không có provider bean, starter có thể fallback `Authentication.getAuthorities()`.
- Nhưng với kiến trúc username-only, consumer nên luôn cung cấp provider.

---

## 2. Role động

Role vẫn do starter quản lý trong bảng:

```text
sec_authority
```

Admin role API của starter có thể giữ:

```text
/api/admin/sec/roles
```

Consumer quản lý user-role assignment trong bảng của consumer, ví dụ:

```text
app_user
app_user_role
```

Lý do: starter không sở hữu user table nên không được biết user nào có role nào. Starter chỉ biết role name sau khi provider trả về.

---

## 3. Permission động

Permission vẫn do starter quản lý trong:

```text
sec_permission
```

Permission matrix key theo sorted authority set:

```text
ROLE_ADMIN,ROLE_USER -> PermissionMatrix
```

Khi admin sửa permission:

- evict permission matrix cache
- evict menu cache nếu có
- nếu product yêu cầu bắt login lại, bump auth version/global epoch

Không cần consumer login lại nếu policy là "request sau dùng permission mới". Nếu policy là "force login", xem mục 7.

---

## 4. Menu động

Menu dynamic phải derive từ username -> authorities -> permissions.

Flow chuẩn:

```text
GET /api/menus/me
-> current username
-> CurrentUserAuthorityProvider.getAuthorities(username)
-> PermissionMatrix for authorities
-> filter menu definitions
-> return visible menu tree
```

Menu definition không nên hard-code theo user. Mỗi menu item nên khai báo requirement, ví dụ:

```yaml
- code: admin-users
  label: Users
  path: /admin/users
  required:
    action: READ
    target: com.acme.app.domain.UserAdminScreen
    targetType: MENU
```

Hoặc nếu dùng entity permission:

```yaml
required:
  action: READ
  target: com.acme.app.domain.Invoice
  targetType: ENTITY
```

Starter filter menu bằng permission matrix hiện tại.

---

## 5. Cache strategy

Cache nên tách rõ:

| Cache | Key | Value | Owner |
|---|---|---|---|
| `userAuthoritiesByUsername` | username | authority names | starter hoặc consumer provider |
| `sec-permission-matrix` | sorted authority set | permission matrix | starter |
| `menuByAuthorities` | sorted authority set/menu version | menu tree | starter |

Tên cache hiện có:

- Dùng cache name `AuthorityCacheNames.USER_AUTHORITIES_BY_USERNAME` nếu provider cache role theo username.

Eviction bắt buộc:

- Admin sửa permission -> clear `sec-permission-matrix`, menu cache.
- Admin sửa role definition -> clear `sec-permission-matrix`, menu cache, authority cache nếu role names/display/type ảnh hưởng UI.
- Consumer sửa user-role assignment -> evict authority cache theo username hoặc all entries.
- Consumer disable user -> evict authority cache theo username.

---

## 6. Request-time authorization flow

Mỗi request secured:

```text
Authentication auth = SecurityContextHolder.getContext().getAuthentication()
username = auth.getName()
authorities = CurrentUserAuthorityProvider.getAuthorities(username)
matrix = PermissionMatrixCache.get(authorities)
check CRUD/row/attribute/menu
```

Không phụ thuộc:

- JWT claim `auth`
- starter `UserDetailsService`
- starter `SecurityIdentityService`
- starter login endpoint

Consumer có thể dùng JWT/session/SSO gì cũng được, miễn `Authentication.getName()` trả username đúng.

---

## 7. Force login lại khi quyền thay đổi

Clear cache chỉ đảm bảo request sau đọc authority/permission mới. Nếu yêu cầu business là "update quyền thì token/session cũ phải bị đá về login", cần thêm auth version/epoch.

Chuẩn đơn giản:

```text
authVersion: global long trong Hazelcast/DB
```

Consumer token/session chứa version lúc login. Starter hoặc consumer check version mỗi request.

Khi admin sửa permission/role:

```text
authVersion++
clear permission/menu/authority caches
```

Request dùng token/session version cũ:

```text
401 Unauthorized -> frontend logout local -> login lại
```

Nếu chỉ muốn đá user thuộc role bị sửa, dùng role-level version:

```text
roleVersion[ROLE_USER]++
token chứa version từng role
```

Nhưng mặc định nên dùng global version vì đơn giản, chắc chắn, dễ debug.

---

## 8. Những thứ phải gỡ khỏi starter trong mode này

Starter không nên cung cấp:

- `/api/authenticate`
- JWT encoder/decoder bắt buộc
- `DomainUserDetailsService`
- `SecurityIdentityService`
- register/account/change-password/user-admin APIs
- user repository/service/entity concrete

Starter có thể cung cấp tùy chọn:

- `SecurityUser<ID>` mapped superclass implements `UserDetails`
- `CurrentUserAuthorityProvider` SPI
- dynamic permission/menu services
- security context bridge đọc username/authorities

---

## Checklist implementation

- [ ] Có SPI `CurrentUserAuthorityProvider`.
- [ ] `SecurityContextBridge` resolve authorities qua provider theo username, fallback Spring authorities.
- [ ] `RequestPermissionSnapshot` dùng provider thay vì chỉ đọc `Authentication.getAuthorities()`.
- [ ] Menu service dùng cùng authority resolver.
- [ ] Cache authority theo username nếu provider query DB/SSO.
- [ ] Permission/role write path evict permission matrix + menu cache.
- [ ] Consumer user-role write path evict authority cache.
- [ ] Nếu cần force login, thêm auth version/epoch và check mỗi request.
- [ ] Gỡ hoặc optional hóa authentication endpoints/config của starter.
- [ ] README ghi rõ consumer sở hữu login/logout/register/SSO.
