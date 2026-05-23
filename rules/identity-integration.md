# Rule: Identity/User integration cho consumer

> Áp dụng khi consumer nhúng `security-core` và muốn dùng bảng user riêng.
> Starter chỉ cung cấp security pipeline; consumer sở hữu user lifecycle.

---

## Chuẩn hiện tại

Starter **không tạo bảng user mặc định** và **không ship register/account/user-management API mặc định**.

Consumer phải tự sở hữu:

- Entity user, ví dụ `AppUser`
- Migration tạo bảng user và bảng join role
- Repository user
- Registration/account/change-password/user-admin service + REST
- Seed admin/user ban đầu nếu cần
- Implementation của `SecurityIdentityService`

Starter vẫn cung cấp:

- `POST /api/authenticate`
- JWT encode/decode
- `SecurityUser<ID>` base class tùy chọn
- `Authority` / `sec_authority`
- Permission/RBAC/row-level/attribute-level security
- `SecurityUtils`

---

## 1. Entity user chuẩn

Consumer nên đặt tên entity theo domain của app, **không dùng tên `User` nếu dễ trùng import**. Ví dụ:

```java
@Entity
@Table(name = "app_user")
@AssociationOverride(
    name = "authorities",
    joinTable = @JoinTable(
        name = "app_user_authority",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "authority_name")
    )
)
public class AppUser extends SecurityUser<UUID> {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
```

`SecurityUser<ID>` đã implement Spring Security `UserDetails`:

- `getUsername()` = `login`
- `getPassword()` = `password_hash`
- `getAuthorities()` = `Set<Authority>`
- `isEnabled()` = `activated`
- account non-expired / non-locked / credentials non-expired mặc định `true`

Nếu app cần lock/expiry riêng, override các method `UserDetails` tương ứng trong entity con.

---

## 2. Repository user là ngoại lệ hợp lệ

Rule chung: consumer không tạo `JpaRepository` cho entity nghiệp vụ. Nhưng user/identity store là ngoại lệ vì consumer sở hữu authentication source.

```java
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findOneByLogin(String login);

    @EntityGraph(attributePaths = "authorities")
    Optional<AppUser> findOneWithAuthoritiesByLogin(String login);
}
```

Luôn load authorities cùng user trong login flow để tránh lazy-loading issue và để JWT có role đúng.

---

## 3. Bắt buộc implement `SecurityIdentityService`

`DomainUserDetailsService` của starter gọi bean này khi login. Consumer phải cung cấp implementation.

```java
@Service
public class AppSecurityIdentityService implements SecurityIdentityService {

    private final AppUserRepository users;

    public AppSecurityIdentityService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public SecurityPrincipal loadByLogin(String login) {
        String normalizedLogin = login.toLowerCase(Locale.ENGLISH);
        AppUser user = users.findOneWithAuthoritiesByLogin(normalizedLogin)
            .orElseThrow(() -> new UsernameNotFoundException(normalizedLogin));

        if (!user.isEnabled()) {
            throw new UserNotActivatedException("User " + normalizedLogin + " was not activated");
        }

        String userId = user.getId().toString();
        return new DefaultSecurityPrincipal(
            userId,
            user.getUsername(),
            user.getPassword(),
            user.isEnabled(),
            user.getAuthorities(),
            Map.of(SecurityUtils.USER_ID_CLAIM, userId)
        );
    }
}
```

Sau đó login vẫn dùng endpoint của starter:

```text
POST /api/authenticate
```

---

## 4. Registration/account/user-admin do consumer viết

Các luồng cũ cần viết trong consumer:

- `POST /api/register`
- `GET/POST /api/account`
- `POST /api/account/change-password`
- `/api/admin/users/**`
- seed admin user
- check login trùng
- DTO/VM trả về UI

Registration tối thiểu:

```java
@Service
@Transactional
public class AppRegistrationService {

    private final AppUserRepository users;
    private final AuthorityRepository authorities;
    private final PasswordEncoder passwordEncoder;

    public AppUser register(String login, String rawPassword) {
        String normalizedLogin = login.toLowerCase(Locale.ENGLISH);
        users.findOneByLogin(normalizedLogin).ifPresent(existing -> {
            throw new IllegalArgumentException("Login already exists");
        });

        Authority roleUser = authorities.findById(AuthoritiesConstants.USER)
            .orElseThrow();

        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setLogin(normalizedLogin);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setActivated(true);
        user.setAuthorities(Set.of(roleUser));
        return users.save(user);
    }
}
```

---

## 5. Database/migration consumer phải có

Consumer migration cần tạo:

- Bảng user riêng, ví dụ `app_user`
- Bảng join role, ví dụ `app_user_authority`
- FK `app_user_authority.authority_name -> sec_authority.name`
- Seed user đầu tiên nếu cần

Starter migration chỉ seed/hỗ trợ bảng hạ tầng security như `sec_authority` và permission-related tables. Không trông chờ starter tạo `sec_user`.

---

## 6. Cache/permission freshness

Nếu consumer cache user theo login, dùng cache name chung:

```java
UserCacheNames.USERS_BY_LOGIN
```

Khi consumer update user authorities, activated flag, password, hoặc login, consumer phải evict cache entry/allEntries tương ứng. Starter đã evict cache này khi sửa role/permission qua `SecPermissionService` và `SecRoleAdminResource`.

---

## Checklist review

- [ ] Consumer có entity user riêng extend `SecurityUser<ID>` hoặc implement `UserDetails` tương đương?
- [ ] Entity user có `@Id` riêng và table riêng, không dùng `sec_user`?
- [ ] Mapping authorities có join table rõ ràng?
- [ ] Có `SecurityIdentityService` bean?
- [ ] Login flow load user kèm authorities?
- [ ] Registration/account/change-password/user-admin nằm ở consumer?
- [ ] Migration tạo user table + join table + seed admin nếu cần?
- [ ] Khi sửa user authorities có evict `UserCacheNames.USERS_BY_LOGIN` nếu dùng cache?
