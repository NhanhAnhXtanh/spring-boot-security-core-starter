# Spring Boot Security Core Starter

Reusable Spring Boot starter cung cấp sẵn JWT authentication, RBAC (role + permission + row-policy + attribute-level), auditing, fetch plans và security utilities cho các microservice nội bộ.

- Spring Boot 4.0.x
- Java 21
- Auto-configuration: tự kích hoạt khi add dependency
- Repo: <https://github.com/NhanhAnhXtanh/spring-boot-security-core-starter>

## Mục lục

- [Tính năng chính](#tính-năng-chính)
- [Cài đặt](#cài-đặt)
- [Cấu hình tối thiểu](#cấu-hình-tối-thiểu)
- [Properties tham khảo](#properties-tham-khảo)
- [REST endpoints có sẵn](#rest-endpoints-có-sẵn)
- [Rules bắt buộc cho consumer project](#rules-bắt-buộc-cho-consumer-project) ← **đọc trước khi viết code**
- [Override bean mặc định](#override-bean-mặc-định)
- [Tắt từng auto-config](#tắt-từng-auto-config)
- [Quick start cho DEV (auto-seed admin)](#quick-start-cho-dev-auto-seed-admin)
- [Seed SQL (tuỳ chọn)](#seed-sql-tuỳ-chọn)
- [Build từ source](#build-từ-source)
- [License](#license)

## Tính năng chính

| Module | Mô tả |
|---|---|
| **JWT authentication** | HS512, login `/api/authenticate`, register `/api/register`, activate, reset password. |
| **RBAC + ABAC** | `@SecuredEntity` + `SecuredEntityCatalog` + permission table `sec_permission` (action × target × authority × effect). |
| **Row-level security** | Row policy evaluator áp filter trên mọi `SecureDataManager.loadList/loadOne`. |
| **Attribute-level check** | `EntityMutation.changedAttributes` quyết định cột nào được phép set. |
| **Fetch plans** | `fetch-plans.yml` — entity → projection (giảm over-fetch, kiểm soát serialization). |
| **Auditing** | `AbstractAuditingEntity` + `SpringSecurityAuditorAware` (createdBy/Date, lastModifiedBy/Date). |
| **CORS / Security headers** | Cấu hình qua `security-core.cors.*`. |
| **Cache (Hazelcast)** | Bật/tắt qua `security-core.cache.enabled`. |
| **Liquibase migrations** | Schema `sec_*` chạy sẵn khi consumer trỏ `change-log: classpath:config/liquibase/master.xml`. |

## Cài đặt

### Gradle

```groovy
repositories {
    mavenCentral()
    maven {
        url 'https://maven.pkg.github.com/NhanhAnhXtanh/spring-boot-security-core-starter'
        credentials {
            username = project.findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR')
            password = project.findProperty('gpr.token') ?: System.getenv('GITHUB_TOKEN')
        }
    }
}

dependencies {
    implementation 'com.vn.security.core:security-core:0.0.3'
}
```

### Maven

```xml
<dependency>
    <groupId>com.vn.security.core</groupId>
    <artifactId>security-core</artifactId>
    <version>0.0.3</version>
</dependency>
```

## Cấu hình tối thiểu

Trong `application.yml` của consumer:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/your_db
    username: postgres
    password: secret
  jpa:
    hibernate:
      ddl-auto: update
  liquibase:
    change-log: classpath:config/liquibase/master.xml

security-core:
  security:
    jwt:
      base64-secret: <base64-encoded-secret-key>     # bắt buộc — dùng cho HS512
      token-validity-in-seconds: 86400
      token-validity-in-seconds-for-remember-me: 2592000
  cors:
    allowed-origins: 'https://your-app.com'
    allowed-methods: '*'
    allowed-headers: '*'
    allow-credentials: true
    max-age: 1800
  fetch-plans:
    config: classpath:fetch-plans.yml
```

> JWT secret cần đủ dài cho thuật toán HS512 (≥ 512 bit). Sinh nhanh bằng:
> ```bash
> openssl rand -base64 64
> ```

## Properties tham khảo

| Key | Mặc định | Mô tả |
|---|---|---|
| `security-core.security.jwt.base64-secret` | — | Khóa bí mật cho JWT (Base64). |
| `security-core.security.jwt.token-validity-in-seconds` | `0` | Thời hạn token thường. |
| `security-core.security.jwt.token-validity-in-seconds-for-remember-me` | `0` | Thời hạn token "remember me". |
| `security-core.cors.allowed-origins` | — | Danh sách origin được phép (csv). |
| `security-core.cors.allowed-methods` | `*` | HTTP methods cho phép. |
| `security-core.cors.allowed-headers` | `*` | Headers cho phép. |
| `security-core.cors.allow-credentials` | `false` | Cho phép cookie/credentials. |
| `security-core.cors.max-age` | `1800` | TTL preflight (giây). |
| `security-core.fetch-plans.config` | `classpath:fetch-plans.yml` | File định nghĩa fetch plans. |
| `security-core.liquibase.async-start` | `false` | Bật Liquibase chạy async. |
| `security-core.cache.enabled` | `true` | Bật/tắt Hazelcast cache. |
| `security-core.seed.enabled` | `false` | Tạo user `admin` mặc định khi boot. **CHỈ DÙNG DEV.** |
| `security-core.seed.username` | `admin` | Username cho seed user. |
| `security-core.seed.password` | `admin` | Password cho seed user. |
| `security-core.seed.email` | `admin@localhost` | Email cho seed user. |

## REST endpoints có sẵn

| Method | Path | Mô tả |
|---|---|---|
| POST | `/api/authenticate` | Login → trả JWT. |
| GET | `/api/authenticate` | Check user hiện tại có authenticated không. |
| POST | `/api/register` | Đăng ký account. |
| GET | `/api/activate` | Kích hoạt account qua key. |
| POST | `/api/account/reset-password/init` | Yêu cầu reset password. |
| POST | `/api/account/reset-password/finish` | Hoàn tất reset password. |
| `*` | `/api/admin/**` | Yêu cầu authority `ROLE_ADMIN`. |
| `*` | `/api/**` | Yêu cầu authenticated. |

## Rules bắt buộc cho consumer project

> **Đây là phần quan trọng nhất cho người mới / AI assistant.** Đọc trước khi viết bất kỳ entity / service / controller nào trong project consumer. Vi phạm các rule này sẽ phá vỡ RBAC, row-level filter, audit, hoặc fetch plan của starter.

| Tài liệu | Khi nào đọc | Tóm tắt |
|---|---|---|
| [`rules/data-access.md`](rules/data-access.md) | Trước khi viết bất kỳ code đụng DB | Bắt buộc dùng `SecureDataManager` (CRUD nghiệp vụ) hoặc `UnconstrainedDataManager` (system/bootstrap/migration). **Không tạo thêm `JpaRepository`** ngoài `UserRepository` và `AuthorityRepository`. |
| [`rules/entity-onboarding.md`](rules/entity-onboarding.md) | Khi thêm entity mới hoặc refactor entity cũ | Quy trình 6 bước: tạo entity + `@SecuredEntity` → `@EntityScan` → migration → seed permission → fetch plan → service/REST. Có checklist review PR và bảng "bẫy hay gặp". |

### Cheat-sheet 30 giây

```
Code đụng DB?
├─ User request (REST/GraphQL)?    → SecureDataManager
├─ Bootstrap/seed/migration/job?   → UnconstrainedDataManager (+ comment lý do)
├─ Đang implement check permission? → UnconstrainedDataManager (recursion guard)
└─ Khi nghi ngờ                     → SecureDataManager (an toàn hơn)

Entity mới?
└─ @SecuredEntity (bắt buộc) + @EntityScan package + seed sec_permission + fetch-plans.yml
```

## Override bean mặc định

Hầu hết bean của starter đều dùng `@ConditionalOnMissingBean`. Khai báo bean cùng type trong consumer là override luôn:

```java
@Configuration
public class MySecurityOverride {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 1 << 16, 3);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // chain custom hoàn toàn — starter sẽ tự skip
        return http.build();
    }
}
```

## Tắt từng auto-config

```yaml
spring:
  autoconfigure:
    exclude:
      - com.vn.security.core.autoconfigure.SecurityCoreAutoConfiguration
```

Hoặc tắt theo nhóm qua property:

```yaml
security-core:
  cache:
    enabled: false       # bỏ Hazelcast cache config
spring:
  liquibase:
    enabled: false       # bỏ Liquibase
```

Bỏ Hazelcast / Liquibase ra khỏi classpath cũng tự khiến config tương ứng không kích hoạt (nhờ `@ConditionalOnClass`).

## Quick start cho DEV (auto-seed admin)

Bật 1 dòng trong `application.yml` để starter tự tạo user `admin/admin` khi boot:

```yaml
security-core:
  seed:
    enabled: true
```

Sau khi boot:
```bash
curl -X POST http://localhost:8080/api/authenticate \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin","rememberMe":false}'
```

→ Trả JWT ngay.

> **⚠ KHÔNG BẬT TRÊN PRODUCTION.** Default credentials là CVE category. Production phải để `enabled: false` (mặc định) và seed user thủ công qua migration.

Có thể đổi mật khẩu mặc định:
```yaml
security-core:
  seed:
    enabled: true
    username: superadmin
    password: my-strong-dev-password
    email: dev@example.com
```

## Seed SQL (tuỳ chọn)

Starter có sẵn 2 file seed ở `classpath:db/seed/`:
- `seed-admin.sql` — tạo admin user.
- `seed-permissions.sql` — seed permission/role baseline.

Tham chiếu từ Liquibase changelog của consumer nếu muốn dùng:

```xml
<sqlFile path="db/seed/seed-permissions.sql" relativeToChangelogFile="false"/>
```

## Build từ source

Clone repo và build:

```bash
git clone https://github.com/NhanhAnhXtanh/spring-boot-security-core-starter.git
cd spring-boot-security-core-starter
./gradlew clean build -x test
./gradlew publishToMavenLocal     # cài vào ~/.m2 để consumer test local
```

Yêu cầu: JDK 21 (đã có Gradle wrapper, không cần cài Gradle riêng).

## Đóng góp

PR phải tuân thủ rule trong [`rules/`](rules/). Trước khi mở PR, chạy local:

```bash
./gradlew check
```

Báo issue: <https://github.com/NhanhAnhXtanh/spring-boot-security-core-starter/issues>

## License

Apache License 2.0.
