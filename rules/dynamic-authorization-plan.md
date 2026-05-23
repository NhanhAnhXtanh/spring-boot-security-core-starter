# Plan: Tách authentication khỏi starter, giữ dynamic authorization

> Kế hoạch implement cho mode mới: consumer làm toàn bộ login/logout/register/SSO,
> `security-core` chỉ phân quyền dựa trên username.

---

## Trạng thái trước refactor

Trước khi chuyển sang username-only authorization, code còn một số phần authentication trong starter:

- `AuthenticateController` expose `/api/authenticate`
- `SecurityJwtConfiguration` tạo `JwtDecoder`, `JwtEncoder`, `jwtAuthenticationConverter`
- `SecurityConfiguration` cấu hình OAuth2 resource server và permit `/api/authenticate`
- `DomainUserDetailsService` + `SecurityIdentityService`
- `SecurityPrincipal`, `AbstractSecurityPrincipal`, `DefaultSecurityPrincipal`

Authorization engine hiện đã có:

- `SecurityContextBridge`
- `MergedSecurityContextBridge`
- `RequestPermissionSnapshot`
- `MergedSecurityService`
- `SecPermissionService`
- `CurrentUserMenuPermissionService`

Những phần này đã được đưa vào danh sách cần gỡ để starter chỉ giữ authorization.

---

## Phase 1: Thêm authority resolver theo username

Thêm SPI:

```java
public interface CurrentUserAuthorityProvider {
    Collection<String> getAuthorities(String username);
}
```

Thêm service trung tâm:

```java
public interface CurrentUserAuthorityResolver {
    Collection<String> resolveAuthorities(Authentication authentication);
}
```

Implementation:

1. Lấy username từ `authentication.getName()`.
2. Nếu có `CurrentUserAuthorityProvider` bean -> gọi provider.
3. Nếu không có provider -> fallback `authentication.getAuthorities()`.
4. Validate authority names với `sec_authority` để drop phantom role nếu cần.
5. Cache theo username nếu provider query DB/SSO.

Files dự kiến:

- add `security/CurrentUserAuthorityProvider.java`
- add `security/CurrentUserAuthorityResolver.java`
- add `security/DefaultCurrentUserAuthorityResolver.java`

---

## Phase 2: Sửa authorization engine dùng resolver

Sửa:

- `MergedSecurityContextBridge`
- `RequestPermissionSnapshot`

Trước:

```text
Authentication.getAuthorities()
principal instanceof UserDetails
principal instanceof AcceptsGrantedAuthorities
```

Sau:

```text
CurrentUserAuthorityResolver.resolveAuthorities(authentication)
```

Kết quả:

- SSO/JWT/session của consumer đều chạy được.
- Starter không phụ thuộc JWT claim `auth`.
- Permission matrix key vẫn là sorted authority set.
- Menu động dùng cùng authorities vì `CurrentUserMenuPermissionService` đi qua `MergedSecurityService`.

---

## Phase 3: Tách authentication config khỏi starter

Gỡ khỏi starter:

- `AuthenticateController`
- `SecurityJwtConfiguration`
- `DomainUserDetailsService`
- `SecurityIdentityService`
- `SecurityPrincipal`
- `AbstractSecurityPrincipal`
- `DefaultSecurityPrincipal`

Sửa `SecurityConfiguration`:

Trong mode mới:

- Consumer tự khai báo `SecurityFilterChain`.
- Consumer tự khai báo JWT/session/SSO.
- Starter chỉ đọc `SecurityContextHolder`.

---

## Phase 4: Cache invalidation cho role/permission/menu động

Thêm cache names rõ nghĩa:

```java
USER_AUTHORITIES_BY_USERNAME
PERMISSION_MATRIX_CACHE
MENU_BY_AUTHORITIES
```

Update write paths:

- `SecPermissionService`: evict permission matrix + menu cache.
- `SecRoleAdminResource`: evict authority/menu/permission caches.
- Consumer user-role update: evict `USER_AUTHORITIES_BY_USERNAME` theo username hoặc all entries.

Nếu cần force login:

- Thêm optional `AuthVersionService`.
- Consumer token/session chứa version.
- Admin sửa role/permission -> bump version.
- Request token version cũ -> 401.

Không implement force-login mặc định nếu consumer auth hoàn toàn sở hữu token/session. Starter chỉ cung cấp helper/version service nếu cần.

---

## Phase 5: Docs migration

Cập nhật:

- README: starter không có `/api/authenticate` trong mode mới.
- `rules/cache-debug.md`: đổi cache login cũ thành `userAuthoritiesByUsername`.
- Ví dụ consumer:
  - Spring Security JWT
  - SSO/OAuth2
  - `CurrentUserAuthorityProvider`
  - menu dynamic endpoint

---

## Consumer integration sau khi hoàn tất

Consumer cần:

```java
@Service
public class AppAuthorityProvider implements CurrentUserAuthorityProvider {
    @Override
    public Collection<String> getAuthorities(String username) {
        return appUserRoleService.findRoleNamesByUsername(username);
    }
}
```

Consumer auth/SSO cần đảm bảo:

```java
Authentication.getName() == username
```

Không yêu cầu:

- Consumer dùng starter JWT
- Consumer dùng starter login endpoint

---

## Acceptance criteria

- [ ] Starter build pass khi không có user repository/service của consumer.
- [ ] Starter không expose `/api/authenticate` trong mode mới.
- [ ] Permission check dùng roles từ `CurrentUserAuthorityProvider`.
- [ ] Menu API dùng roles từ cùng resolver.
- [ ] Nếu provider không có bean, fallback Spring authorities vẫn hoạt động.
- [ ] Consumer SSO chỉ cần set `Authentication.getName()`.
- [ ] Update permission/role evict đúng permission/menu cache.
- [ ] Docs nói rõ consumer sở hữu login/logout/register/SSO.
