# Rule: Consumer authentication integration

> Starter chỉ phân quyền theo username. **Toàn bộ authentication (login, logout, register, account, change-password, forgot-password, refresh token, SSO) thuộc consumer.** File này hướng dẫn consumer wire app của họ đúng contract của starter.
>
> Đọc song song [`dynamic-authorization.md`](dynamic-authorization.md) — phần authorization SPI/cache eviction.

---

## Contract bắt buộc với starter

Bất kỳ pattern auth nào consumer chọn (JWT / session / SSO), kết quả cuối cùng phải đảm bảo:

```java
SecurityContextHolder.getContext().getAuthentication().getName() == username
```

Vì:

- `DefaultCurrentUserAuthorityResolver` đọc `Authentication.getName()` → resolve role qua `CurrentUserAuthorityProvider`.
- `MergedSecurityContextBridge.getCurrentUserLogin()` đọc `Authentication.getName()` → set vào auditing (`createdBy`, `lastModifiedBy`).
- `SecurityUtils.getCurrentUserLogin()` đọc qua chain `UserDetails.getUsername() | Jwt.getSubject() | String principal | Authentication.getName()`.

Nếu consumer không đảm bảo contract này, mọi enforcement (CRUD/row/attribute/menu) và audit sẽ sai.

---

## Pattern mặc định: JWT stateless

Phù hợp cho microservice nội bộ. Consumer issue JWT khi login, gateway/service verify token mỗi request.

### 1. `SecurityFilterChain` cho consumer

```java
@Configuration
public class AppSecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh").permitAll()
                .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .jwtAuthenticationConverter(jwtAuthenticationConverter())
            ))
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> res.sendError(401))
                .accessDeniedHandler((req, res, ex) -> res.sendError(403))
            )
            .build();
    }

    /**
     * Drop the JWT 'auth' claim — the starter resolves authorities dynamically via
     * CurrentUserAuthorityProvider, so we don't want stale JWT claims overriding it.
     * Returning an empty authority set forces the resolver path.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> List.of());
        // Make sure principal name = username, not the default sub claim if it's an internal id.
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String base64Secret) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Secret);
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA512");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS512).build();
    }

    @Bean
    public JwtEncoder jwtEncoder(@Value("${app.jwt.secret}") String base64Secret) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Secret);
        OctetSequenceKey key = new OctetSequenceKey.Builder(keyBytes).algorithm(JWSAlgorithm.HS512).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }
}
```

### 2. Login endpoint

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final AppUserService userService;
    private final UserAuthorityCacheService authorityCache;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.username(), req.password())
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Pre-load authority cache so the user's first authenticated request skips the
        // provider lookup. Optional but cheap if the provider hits a DB.
        authorityCache.warmUp(auth.getName());

        AppUser user = userService.findByUsername(auth.getName()).orElseThrow();
        String token = issueToken(user);
        return ResponseEntity.ok(new TokenResponse(token, 86400));
    }

    private String issueToken(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("self")
            .issuedAt(now)
            .expiresAt(now.plus(24, ChronoUnit.HOURS))
            .subject(user.getLogin())                  // Authentication.getName() <- this
            .claim("userId", user.getId().toString())  // SecurityUtils.getCurrentUserId() reads this
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS512).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}

public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
public record TokenResponse(String accessToken, long expiresInSeconds) {}
```

`AuthenticationManager` cần một `UserDetailsService` bean — consumer khai báo:

```java
@Service
public class AppUserDetailsService implements UserDetailsService {
    private final AppUserRepository repo;

    @Override
    public UserDetails loadUserByUsername(String username) {
        return repo.findByLoginIgnoreCase(username)
            .filter(AppUser::isActivated)
            .map(u -> User.builder()
                .username(u.getLogin())
                .password(u.getPassword())  // bcrypt hash, dùng PasswordEncoder bean của starter
                .authorities(List.of())     // starter resolve động, không lấy từ đây
                .build())
            .orElseThrow(() -> new UsernameNotFoundException(username));
    }
}

@Configuration
public class AuthManagerConfig {
    @Bean
    public AuthenticationManager authenticationManager(AppUserDetailsService uds, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(uds);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
```

### 3. Register endpoint

```java
@PostMapping("/register")
public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest req) {
    if (userService.existsByLogin(req.username())) {
        throw new BadRequestAlertException("Username already used", "user", "loginused");
    }
    AppUser user = new AppUser();
    user.setLogin(req.username().toLowerCase(Locale.ENGLISH));
    user.setPassword(passwordEncoder.encode(req.password()));  // starter's BCryptPasswordEncoder
    user.setEmail(req.email());
    user.setActivated(true);
    userService.create(user);

    // Assign default role in the consumer's user-role table (NOT in sec_authority).
    userRoleService.assign(user.getLogin(), "ROLE_USER");

    // Critical: evict so first login after register reads the role just inserted.
    // (Required because the default-role insert happens outside SecPermissionService /
    // SecRoleAdminResource, which are the only auto-evicting write paths in the starter.)
    authorityCache.evict(user.getLogin());

    return ResponseEntity.status(HttpStatus.CREATED).build();
}

public record RegisterRequest(
    @NotBlank @Pattern(regexp = "^[a-z0-9._-]{3,50}$") String username,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotBlank @Email String email
) {}
```

### 4. Logout endpoint

Với JWT stateless thuần, server không track token → logout = client xóa token. Nhưng nếu consumer dùng denylist hoặc muốn evict cache:

```java
@PostMapping("/logout")
public ResponseEntity<Void> logout(Authentication auth) {
    if (auth != null) {
        // Optional: add JWT id to denylist (Redis/Hazelcast) with TTL = remaining lifetime.
        // tokenDenylist.add(jti, expiresAt);

        // Drop cached authorities so a future login with the same username re-reads roles
        // (useful if admin changed roles while user was logged in).
        authorityCache.evict(auth.getName());
    }
    SecurityContextHolder.clearContext();
    return ResponseEntity.noContent().build();
}
```

### 5. Change password / forgot password

```java
@PostMapping("/change-password")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req, Authentication auth) {
    AppUser user = userService.findByUsername(auth.getName()).orElseThrow();
    if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
        throw new BadRequestAlertException("Wrong current password", "user", "wrongpw");
    }
    user.setPassword(passwordEncoder.encode(req.newPassword()));
    userService.save(user);

    // Password change does NOT change roles, so authority cache is fine. But if business
    // requires "change password -> sign out everywhere", denylist all the user's outstanding
    // tokens here and evict the authority cache as a defense-in-depth measure.
    return ResponseEntity.noContent().build();
}

@PostMapping("/forgot-password/init")
public ResponseEntity<Void> requestReset(@Valid @RequestBody ForgotPasswordRequest req) {
    userService.findByEmail(req.email()).ifPresent(user -> {
        String token = resetTokenService.issue(user.getLogin());  // store token + TTL
        mailer.sendResetLink(user.getEmail(), token);
    });
    // Always return 204 to avoid leaking which emails are registered.
    return ResponseEntity.noContent().build();
}

@PostMapping("/forgot-password/finish")
public ResponseEntity<Void> finishReset(@Valid @RequestBody ResetPasswordRequest req) {
    String username = resetTokenService.consume(req.token()).orElseThrow(() ->
        new BadRequestAlertException("Invalid or expired token", "user", "badtoken"));
    AppUser user = userService.findByUsername(username).orElseThrow();
    user.setPassword(passwordEncoder.encode(req.newPassword()));
    userService.save(user);
    return ResponseEntity.noContent().build();
}
```

### 6. Refresh token

```java
@PostMapping("/refresh")
public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
    String username = refreshTokenService.consume(req.refreshToken()).orElseThrow(() ->
        new BadRequestAlertException("Invalid refresh token", "auth", "badrefresh"));
    AppUser user = userService.findByUsername(username).orElseThrow();
    String accessToken = issueToken(user);
    String newRefresh = refreshTokenService.rotate(username);   // single-use rotation
    return ResponseEntity.ok(new TokenResponse(accessToken, newRefresh, 86400));
}
```

Refresh token nên: lưu hash trong DB/Redis, single-use rotation, TTL dài hơn access token (7-30 ngày), revoke khi logout/change-password.

---

## JWT claim convention với `SecurityUtils`

Starter cung cấp helper:

| Helper | Đọc từ đâu | Consumer cần set claim |
|---|---|---|
| `SecurityUtils.getCurrentUserLogin()` | `Authentication.getName()` (qua `Jwt.getSubject()`) | `sub` = username |
| `SecurityUtils.getCurrentUserIdAsString()` | claim `userId` trên `Jwt` | `userId` = id của AppUser (string/UUID/Long đều OK) |
| `SecurityUtils.getCurrentUserId()` | claim `userId` parse `Long` | `userId` numeric |
| `SecurityUtils.getCurrentUserUuid()` | claim `userId` parse `UUID` | `userId` UUID string |

Nếu consumer không set claim `userId` thì các helper trả `Optional.empty()` — không lỗi, nhưng code dùng helper phải xử lý empty.

---

## Pattern thay thế: Session-based

Phù hợp khi consumer có monolith, dùng cookie session.

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .formLogin(form -> form
            .loginProcessingUrl("/api/auth/login")
            .successHandler((req, res, auth) -> {
                authorityCache.warmUp(auth.getName());
                res.setStatus(200);
            })
            .failureHandler((req, res, ex) -> res.sendError(401))
        )
        .logout(logout -> logout
            .logoutUrl("/api/auth/logout")
            .logoutSuccessHandler((req, res, auth) -> {
                if (auth != null) authorityCache.evict(auth.getName());
                res.setStatus(204);
            })
        )
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
            .anyRequest().authenticated()
        )
        .build();
}
```

Spring Security mặc định gọi `UserDetailsService` để authenticate form login → `Authentication.getName()` = `UserDetails.getUsername()` → contract OK.

---

## Pattern thay thế: OAuth2 / SSO

Phù hợp khi auth ngoài (Keycloak, Auth0, Okta, Google).

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .oauth2Login(oauth2 -> oauth2
            .userInfoEndpoint(ui -> ui.oidcUserService(customOidcUserService()))
        )
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
            .anyRequest().authenticated()
        )
        .build();
}

@Bean
public OAuth2UserService<OidcUserRequest, OidcUser> customOidcUserService() {
    OidcUserService delegate = new OidcUserService();
    return userRequest -> {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        String username = oidcUser.getPreferredUsername();   // or .getEmail()

        // Auto-provision local AppUser if absent so consumer-side joins still work.
        userService.findByUsername(username).orElseGet(() -> userService.createFromOidc(oidcUser));

        // Wrap so Authentication.getName() returns the username we want, not the OIDC sub.
        return new DefaultOidcUser(
            oidcUser.getAuthorities(),
            oidcUser.getIdToken(),
            oidcUser.getUserInfo(),
            "preferred_username"
        );
    };
}
```

Quan trọng: chọn claim làm `Authentication.getName()` (`preferred_username` / `email` / custom). Việc map group SSO → role app: consumer làm trong `CurrentUserAuthorityProvider`.

---

## Checklist trước khi đưa consumer app lên production

- [ ] `Authentication.getName()` luôn trả về username matching `app_user.login`.
- [ ] `SecurityFilterChain` consumer khai báo (starter không có).
- [ ] Login flow gọi `authorityCache.warmUp(username)` (optional, để skip provider lookup ở request đầu).
- [ ] Logout flow gọi `authorityCache.evict(username)`.
- [ ] Register flow assign default role và gọi `authorityCache.evict(username)`.
- [ ] Disable user / unassign role gọi `authorityCache.evict(username)`.
- [ ] Bulk role remap gọi `authorityCache.evictAll()`.
- [ ] `CurrentUserAuthorityProvider` implement trả về `sec_authority.name` đã seed.
- [ ] Default role `ROLE_USER` (và mọi role consumer dùng) đã seed trong `sec_authority`.
- [ ] Password lưu dạng bcrypt hash (dùng `PasswordEncoder` bean của starter).
- [ ] JWT claim `sub` = username, `userId` = AppUser id (nếu dùng `SecurityUtils.getCurrentUserId*`).
- [ ] Refresh token single-use rotation, revoke trên logout/change-password.
- [ ] Forgot-password endpoint không leak existence của email.
- [ ] CSRF: enable cho session/cookie mode, disable cho JWT stateless mode.
- [ ] Rate limit login/register/refresh (consumer tự dựng — starter không có).
