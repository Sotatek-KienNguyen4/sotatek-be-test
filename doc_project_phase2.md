### Review Tổng Quát Phase 2 Của Bạn

```markdown
### Phase 2: Domain Modeling and Persistence (Estimated: 25–35 minutes)
Mục tiêu: Xây dựng domain model chính xác, persistence layer version-controlled, và verify sớm qua test. Ưu tiên clean domain (DDD-inspired), fault-tolerance (UUID), và auditability (full Spring Data JPA auditing).

#### Phase 2a: Define Domain Model & Entity Structure (Estimated: 6–8 minutes)
- **Bước 1**: Tạo package `com.example.orderservice.domain` (nếu chưa).
- **Bước 2**: Tạo enum `OrderStatus`:
  ```java
  package com.example.orderservice.domain;

  public enum OrderStatus {
      PENDING, CONFIRMED, PAID, SHIPPED, DELIVERED, CANCELLED
  }
  ```
- **Bước 3**: Tạo entity `Order` (full auditing + optimistic locking):
  ```java
  package com.example.orderservice.domain;

  import jakarta.persistence.*;
  import lombok.*;
  import org.hibernate.annotations.GenericGenerator;
  import org.springframework.data.annotation.CreatedDate;
  import org.springframework.data.annotation.LastModifiedDate;
  import org.springframework.data.jpa.domain.support.AuditingEntityListener;

  import java.math.BigDecimal;
  import java.time.Instant;
  import java.util.UUID;

  @Entity
  @Table(name = "orders")
  @EntityListeners(AuditingEntityListener.class)  // Enable auditing
  @Getter
  @Setter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public class Order {

      @Id
      @GeneratedValue(generator = "uuid2")
      @GenericGenerator(name = "uuid2", strategy = "org.hibernate.id.UUIDGenerator")
      @Column(columnDefinition = "uuid")
      private UUID id;

      @Column(nullable = false)
      private UUID memberId;

      @Column(nullable = false)
      private UUID productId;

      @Column(nullable = false)
      private Integer quantity;

      @Column(nullable = false, precision = 10, scale = 2)
      private BigDecimal totalPrice;

      @Enumerated(EnumType.STRING)
      @Column(nullable = false)
      private OrderStatus status = OrderStatus.PENDING;

      @CreatedDate
      @Column(updatable = false)
      private Instant createdAt;

      @LastModifiedDate
      private Instant updatedAt;

      @Version  // Optimistic locking - senior best practice cho concurrent updates
      private Long version;
  }
  ```
  **Lý do senior**:
  - UUID với `UUIDGenerator` → reliable generation ở distributed env.
  - `@CreatedDate` / `@LastModifiedDate` + `AuditingEntityListener` → chuẩn Spring Data JPA auditing (timezone-safe với Instant).
  - `@Version` → prevent lost updates trong microservice concurrent scenarios.
  - BigDecimal cho tiền, Instant cho time.

- **Checklist**:
  - Compile không lỗi.
  - Sử dụng `jakarta.persistence.*` (Spring Boot 3+).
- **Milestone**: Entity hoàn chỉnh, compile ok.

#### Phase 2b: Configure JPA Auditing & Flyway (Estimated: 5–7 minutes)
- **Bước 1**: Tạo config auditing (nếu chưa có ở Phase 1):
  ```java
  package com.example.orderservice.infrastructure.config;

  import org.springframework.context.annotation.Configuration;
  import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

  @Configuration
  @EnableJpaAuditing  // Kích hoạt auditing
  public class JpaConfig {
  }
  ```
  (Nếu cần audit by user sau, implement `AuditorAware<String>` và ref trong `@EnableJpaAuditing(auditorAwareRef = "...")`).

- **Bước 2**: Cập nhật `application.yml` (hoặc application-dev.yml):
  ```yaml
  spring:
    jpa:
      hibernate:
        ddl-auto: none  # Bắt buộc khi dùng Flyway
      show-sql: true
      properties:
        hibernate:
          format_sql: true
    flyway:
      enabled: true
      validate-migration-naming: true  # Enforce naming convention
      locations: classpath:db/migration
  ```
- **Checklist**:
  - `ddl-auto: none` tránh conflict Flyway.
  - `validate-migration-naming: true` → catch lỗi naming sớm.

#### Phase 2c: Set Up Flyway Migration (Estimated: 6–8 minutes)
- **Bước 1**: Tạo thư mục `src/main/resources/db/migration`.
- **Bước 2**: Tạo `V1__create_orders_table.sql` (full constraints, indexes):
  ```sql
  CREATE TABLE orders (
      id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      member_id       UUID NOT NULL,
      product_id      UUID NOT NULL,
      quantity        INTEGER NOT NULL CHECK (quantity > 0),
      total_price     DECIMAL(10,2) NOT NULL CHECK (total_price >= 0),
      status          VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
      created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
      updated_at      TIMESTAMP WITH TIME ZONE,
      version         BIGINT NOT NULL DEFAULT 0
  );

  CREATE INDEX idx_orders_member_id ON orders(member_id);
  CREATE INDEX idx_orders_status ON orders(status);
  CREATE INDEX idx_orders_created_at ON orders(created_at);
  ```
  **Lý do**: CHECK cho status (consistent với enum), version cho optimistic lock, indexes cho query phổ biến (list by member/status).

- **Checklist**:
  - Naming chuẩn: `V1__...`
  - Run app → Flyway tự apply, bảng tạo đúng.
- **Milestone**: Schema versioned, indexes sẵn.

#### Phase 2d: Create Repository & Enhanced Tests (Estimated: 6–9 minutes)
- **Bước 1**: Repository:
  ```java
  package com.example.orderservice.infrastructure.repository;

  import com.example.orderservice.domain.Order;
  import org.springframework.data.jpa.repository.JpaRepository;
  import java.util.UUID;

  public interface OrderRepository extends JpaRepository<Order, UUID> {
      // Thêm sau nếu cần: List<Order> findByMemberId(UUID memberId, Pageable pageable);
  }
  ```

- **Bước 2**: Test (verify auditing + version):
  ```java
  package com.example.orderservice.infrastructure.repository;

  import com.example.orderservice.domain.Order;
  import com.example.orderservice.domain.OrderStatus;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
  import java.math.BigDecimal;
  import java.util.UUID;
  import static org.assertj.core.api.Assertions.assertThat;

  @DataJpaTest
  class OrderRepositoryTest {

      @Autowired
      private OrderRepository repository;

      @Test
      void shouldSaveAndFindOrderWithAuditing() {
          Order order = Order.builder()
                  .memberId(UUID.randomUUID())
                  .productId(UUID.randomUUID())
                  .quantity(3)
                  .totalPrice(new BigDecimal("150.00"))
                  .status(OrderStatus.PENDING)
                  .build();

          Order saved = repository.save(order);

          assertThat(saved.getId()).isNotNull();
          assertThat(saved.getCreatedAt()).isNotNull();  // Auditing auto-fill
          assertThat(saved.getUpdatedAt()).isNotNull();
          assertThat(saved.getVersion()).isEqualTo(0L);  // Initial version

          Order found = repository.findById(saved.getId()).orElseThrow();
          assertThat(found).usingRecursiveComparison().isEqualTo(saved);
      }

      @Test
      void shouldIncrementVersionOnUpdate() {
          Order order = Order.builder()
                  .memberId(UUID.randomUUID())
                  .productId(UUID.randomUUID())
                  .quantity(1)
                  .totalPrice(BigDecimal.TEN)
                  .status(OrderStatus.PENDING)
                  .build();

          Order saved = repository.save(order);
          Long initialVersion = saved.getVersion();

          saved.setQuantity(2);
          repository.save(saved);

          Order updated = repository.findById(saved.getId()).orElseThrow();
          assertThat(updated.getVersion()).isGreaterThan(initialVersion);
      }
  }
  ```
- **Bước 3**: Run `./gradlew test --tests OrderRepositoryTest`.
- **Checklist**: Test cover auditing + optimistic lock.

#### Phase 2e: Quick Review & Commit (Estimated: 2–3 minutes)
- Commit message:
  ```
  feat: domain model and persistence layer

  - Order entity with UUID, auditing, optimistic locking
  - Full Spring Data JPA auditing config
  - Flyway V1 migration with constraints & indexes
  - Repository + integration tests verifying save/find/audit/version
  ```
- Push nếu cần.

**Tổng kết Phase 2 – Senior Highlights**:
- Full auditing (timestamp + ready cho user audit).
- Flyway strict + validate naming.
- Optimistic locking (@Version).
- Tests cho auditing & concurrency.
- UUID generation robust.

Khi hoàn thành, bạn đã có domain vững chắc, persistence an toàn, test-backed → sẵn sàng cho Phase 3 (REST APIs). Báo mình nếu cần tiếp tục Phase 3 nhé! Chúc thi tốt! 🚀
```