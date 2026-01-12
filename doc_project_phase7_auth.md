### Kế Hoạch Chi Tiết cho Phần Đăng Nhập Sử Dụng JWT (Estimated: 30–45 minutes)

**Mục tiêu tổng quát**:  
Xây dựng module authentication sử dụng JWT (JSON Web Tokens) cho Order Service, tập trung vào đăng nhập an toàn, token generation/validation, và integration với Spring Security.  
Ưu tiên security best practices (senior level): bcrypt hash password, refresh token optional, role-based access (e.g., USER/ADMIN), error handling meaningful, và logging traceable.  
Giả sử bạn có entity User/Member sẵn (từ database), và tích hợp vào flow API (e.g., protect /api/orders với JWT).  
Phase này standalone nhưng có thể integrate sau Phase 4 (e.g., add @PreAuthorize cho endpoints).  
Tech stack: Spring Boot 3.x/4.x, Spring Security 6.x, jjwt-api (hoặc spring-security-jwt), H2/PostgreSQL cho user storage.

#### Phase Đăng Nhập 1: Setup Dependencies & Configuration Basics (Estimated: 5–7 minutes)
- **Bước 1**: Thêm dependencies vào `build.gradle` (nếu chưa có Spring Security từ Phase 1):
  ```gradle
  // Security core
  implementation 'org.springframework.boot:spring-boot-starter-security'
  // JWT libs (jjwt cho generation/validation)
  implementation 'io.jsonwebtoken:jjwt-api:0.12.3'  // latest stable 2026
  runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.3'
  runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.3'  // JSON parser
  // Optional: refresh token nếu cần (spring-boot-starter-oauth2-resource-server cho full JWT)
  ```
  Chạy `./gradlew build` để verify.

- **Bước 2**: Tạo config class cho Security & JWT (trong `com.example.orderservice.config`):
  ```java
  package com.example.orderservice.config;

  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;
  import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
  import org.springframework.security.crypto.password.PasswordEncoder;

  @Configuration
  public class SecurityConfig {

      @Bean
      public PasswordEncoder passwordEncoder() {
          return new BCryptPasswordEncoder(12);  // Strength 12: balance security/performance
      }

      // JWT secret key (sẽ dùng sau) – lấy từ env var hoặc application.yml
      // @Value("${jwt.secret}") private String jwtSecret;
  }
  ```

- **Bước 3**: Cấu hình JWT properties trong `application.yml`:
  ```yaml
  jwt:
    secret: ${JWT_SECRET:your-secure-random-secret-key-64-chars}  # Generate strong key (e.g., base64 random)
    expiration: 3600000  # 1 giờ (ms)
    refresh-expiration: 604800000  # 7 ngày (optional refresh)
  security:
    ignored-paths: /api/auth/**, /h2-console/**, /swagger-ui/**  # Public paths
  ```

**Lý do senior**: BCrypt cho hash password (slow hash chống brute-force). Secret từ env → secure (không hardcode). Expiration short-lived cho access token.

- **Checklist**:
    - Dependencies resolve không lỗi.
    - Secret key strong (use `openssl rand -base64 64` để generate).
- **Trade-off Note**: jjwt simple → lightweight; nếu cần full OAuth2 → dùng spring-security-oauth2-jose nhưng over cho basic JWT.
- **Pitfall**: Weak secret → security vuln; quên env var → fallback weak default.
- **Milestone**: Build success, password encoder bean sẵn.

#### Phase Đăng Nhập 2: User Entity & Authentication Service (Estimated: 8–10 minutes)
- **Bước 1**: Update hoặc tạo entity User (nếu chưa, trong `domain`):
  ```java
  package com.example.orderservice.domain;

  import jakarta.persistence.*;
  import lombok.*;
  import org.springframework.security.core.GrantedAuthority;
  import org.springframework.security.core.authority.SimpleGrantedAuthority;
  import org.springframework.security.core.userdetails.UserDetails;

  import java.util.Collection;
  import java.util.List;

  @Entity
  @Table(name = "users")
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public class User implements UserDetails {
      @Id
      @GeneratedValue(strategy = GenerationType.UUID)
      private UUID id;

      @Column(unique = true, nullable = false)
      private String username;

      @Column(nullable = false)
      private String password;  // Hashed

      @Column
      private String role;  // e.g., "USER", "ADMIN"

      @Override
      public Collection<? extends GrantedAuthority> getAuthorities() {
          return List.of(new SimpleGrantedAuthority(role));
      }

      @Override
      public boolean isAccountNonExpired() { return true; }
      @Override
      public boolean isAccountNonLocked() { return true; }
      @Override
      public boolean isCredentialsNonExpired() { return true; }
      @Override
      public boolean isEnabled() { return true; }
  }
  ```

- **Bước 2**: Tạo UserRepository (extends JpaRepository<User, UUID>).

- **Bước 3**: Tạo AuthService cho login logic (trong `application.service`):
  ```java
  package com.example.orderservice.application.service;

  import com.example.orderservice.domain.User;
  import com.example.orderservice.exception.AuthenticationFailedException;
  import io.jsonwebtoken.Jwts;
  import io.jsonwebtoken.SignatureAlgorithm;
  import io.jsonwebtoken.security.Keys;
  import lombok.RequiredArgsConstructor;
  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.security.core.userdetails.UserDetailsService;
  import org.springframework.security.core.userdetails.UsernameNotFoundException;
  import org.springframework.security.crypto.password.PasswordEncoder;
  import org.springframework.stereotype.Service;

  import java.util.Date;
  import java.util.HashMap;
  import java.util.Map;

  @Service
  @RequiredArgsConstructor
  public class AuthService implements UserDetailsService {

      private final UserRepository userRepository;
      private final PasswordEncoder passwordEncoder;

      @Value("${jwt.secret}")
      private String jwtSecret;

      @Value("${jwt.expiration}")
      private long jwtExpiration;

      @Override
      public User loadUserByUsername(String username) throws UsernameNotFoundException {
          return userRepository.findByUsername(username)
                  .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
      }

      public String login(String username, String password) {
          User user = loadUserByUsername(username);
          if (!passwordEncoder.matches(password, user.getPassword())) {
              throw new AuthenticationFailedException("Invalid credentials");
          }

          // Generate JWT
          Map<String, Object> claims = new HashMap<>();
          claims.put("role", user.getRole());

          return Jwts.builder()
                  .setClaims(claims)
                  .setSubject(user.getUsername())
                  .setIssuedAt(new Date())
                  .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                  .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS512)
                  .compact();
      }
  }
  ```

**Lý do senior**: Implements UserDetailsService → integrate Spring Security. Claims include role → RBAC. Matches password hashed.

- **Checklist**: Repository custom query nếu cần (findByUsername).
- **Trade-off Note**: HS512 secure → nhưng nếu cần asymmetric → dùng RSA (more complex).
- **Pitfall**: Secret byte[] wrong → signing fail.
- **Milestone**: Service sẵn, có thể unit test login.

#### Phase Đăng Nhập 3: JWT Filter & Security Configuration (Estimated: 8–10 minutes)
- **Bước 1**: Tạo JWT Util class (validation):
  ```java
  package com.example.orderservice.config;

  import io.jsonwebtoken.Claims;
  import io.jsonwebtoken.Jwts;
  import io.jsonwebtoken.security.Keys;
  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
  import org.springframework.security.core.Authentication;
  import org.springframework.security.core.userdetails.UserDetails;
  import org.springframework.stereotype.Component;

  import java.util.function.Function;

  @Component
  public class JwtUtil {

      @Value("${jwt.secret}")
      private String secret;

      public String extractUsername(String token) {
          return extractClaim(token, Claims::getSubject);
      }

      public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
          final Claims claims = extractAllClaims(token);
          return claimsResolver.apply(claims);
      }

      private Claims extractAllClaims(String token) {
          return Jwts.parserBuilder()
                  .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes()))
                  .build()
                  .parseClaimsJws(token)
                  .getBody();
      }

      public boolean isTokenValid(String token, UserDetails userDetails) {
          final String username = extractUsername(token);
          return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
      }

      private boolean isTokenExpired(String token) {
          return extractClaim(token, Claims::getExpiration).before(new Date());
      }

      public Authentication getAuthentication(String token, UserDetails userDetails) {
          return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
      }
  }
  ```

- **Bước 2**: Tạo JwtAuthenticationFilter (extends OncePerRequestFilter):
  ```java
  package com.example.orderservice.config;

  import jakarta.servlet.FilterChain;
  import jakarta.servlet.ServletException;
  import jakarta.servlet.http.HttpServletRequest;
  import jakarta.servlet.http.HttpServletResponse;
  import lombok.RequiredArgsConstructor;
  import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
  import org.springframework.security.core.context.SecurityContextHolder;
  import org.springframework.security.core.userdetails.UserDetails;
  import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
  import org.springframework.stereotype.Component;
  import org.springframework.web.filter.OncePerRequestFilter;

  import java.io.IOException;

  @Component
  @RequiredArgsConstructor
  public class JwtAuthenticationFilter extends OncePerRequestFilter {

      private final JwtUtil jwtUtil;
      private final AuthService authService;

      @Override
      protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
              throws ServletException, IOException {
          String authHeader = request.getHeader("Authorization");
          if (authHeader == null || !authHeader.startsWith("Bearer ")) {
              filterChain.doFilter(request, response);
              return;
          }

          String jwt = authHeader.substring(7);
          String username = jwtUtil.extractUsername(jwt);

          if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
              UserDetails userDetails = authService.loadUserByUsername(username);
              if (jwtUtil.isTokenValid(jwt, userDetails)) {
                  UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                          userDetails, null, userDetails.getAuthorities());
                  authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                  SecurityContextHolder.getContext().setAuthentication(authToken);
              }
          }
          filterChain.doFilter(request, response);
      }
  }
  ```

- **Bước 3**: Config Security full (WebSecurityConfig):
  ```java
  package com.example.orderservice.config;

  import lombok.RequiredArgsConstructor;
  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;
  import org.springframework.security.authentication.AuthenticationManager;
  import org.springframework.security.authentication.AuthenticationProvider;
  import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
  import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
  import org.springframework.security.config.annotation.web.builders.HttpSecurity;
  import org.springframework.security.config.http.SessionCreationPolicy;
  import org.springframework.security.web.SecurityFilterChain;
  import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

  @Configuration
  @RequiredArgsConstructor
  public class WebSecurityConfig {

      private final JwtAuthenticationFilter jwtAuthFilter;
      private final AuthService authService;
      private final PasswordEncoder passwordEncoder;

      @Bean
      public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
          http
                  .csrf().disable()
                  .authorizeHttpRequests()
                  .requestMatchers("/api/auth/**", "/h2-console/**", "/swagger-ui/**").permitAll()
                  .anyRequest().authenticated()
                  .and()
                  .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                  .and()
                  .authenticationProvider(authenticationProvider())
                  .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

          return http.build();
      }

      @Bean
      public AuthenticationProvider authenticationProvider() {
          DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
          authProvider.setUserDetailsService(authService);
          authProvider.setPasswordEncoder(passwordEncoder);
          return authProvider;
      }

      @Bean
      public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
          return config.getAuthenticationManager();
      }
  }
  ```

**Lý do senior**: Stateless session (JWT only). Filter before UsernamePassword để validate token. PermitAll cho /auth/login.

- **Checklist**: Endpoints protected (test unauthorized → 401).
- **Trade-off Note**: Basic auth → simple; OAuth2 nếu over.
- **Pitfall**: Sai filter order → auth fail.
- **Milestone**: Security chain ready, token validate work.

#### Phase Đăng Nhập 4: Login Controller & Tests (Estimated: 5–7 minutes)
- **Bước 1**: Tạo AuthController (adapter.controller):
  ```java
  package com.example.orderservice.adapter.controller;

  import com.example.orderservice.application.service.AuthService;
  import lombok.Data;
  import lombok.RequiredArgsConstructor;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.annotation.PostMapping;
  import org.springframework.web.bind.annotation.RequestBody;
  import org.springframework.web.bind.annotation.RequestMapping;
  import org.springframework.web.bind.annotation.RestController;

  @RestController
  @RequestMapping("/api/auth")
  @RequiredArgsConstructor
  public class AuthController {

      private final AuthService authService;

      @PostMapping("/login")
      public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
          String token = authService.login(request.getUsername(), request.getPassword());
          return ResponseEntity.ok(new AuthResponse(token));
      }
  }

  @Data
  public class AuthRequest {
      private String username;
      private String password;
  }

  @Data
  public class AuthResponse {
      private String token;
  }
  ```

- **Bước 2**: Tests (unit cho service, integration cho controller):
    - Unit test AuthService (Mockito mock repo, encoder).
    - Integration test: @SpringBootTest với MockMvc (POST /login → 200 token, invalid → 401).

**Lý do senior**: Request/Response DTO → clean API. Test cover credentials invalid.

- **Checklist**: Test token parse (valid username/role).
- **Trade-off Note**: No refresh token → simple; add nếu need long-lived sessions.
- **Pitfall**: Password plain in request → always HTTPS in prod.
- **Milestone**: Login work, return JWT, APIs protected.

#### Phase Đăng Nhập 5: Commit & Documentation (Estimated: 3–5 minutes)
- Commit:
  ```
  feat: implement JWT authentication for login

  - User entity with UserDetails
  - AuthService for login & token generation
  - JWT filter & Security config
  - Login controller with DTOs
  - Unit/integration tests for auth flow
  ```

- Update README: Section "Authentication": "JWT-based login at /api/auth/login. Protected APIs require Bearer token. Secret from env."

**Tổng kết – Senior Highlights**:
- Secure hash, role-based, stateless.
- Tests robust, config flexible.
- Sẵn integrate (e.g., @PreAuthorize("hasRole('USER')") cho orders).

**Tips**: Thời gian sát → skip tests, focus login flow. Prod: Rotate secret, add CSRF nếu stateful.