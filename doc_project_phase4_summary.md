```markdown
### Phase 4: Mock External Services and Integration (Estimated: 40–55 minutes)

**Mục tiêu**: Tích hợp 3 external services **chỉ trong flow create order** (POST /api/orders) theo đúng thứ tự đề bài:  
1. Validate Member (exists & active)  
2. Check Product (availability & stock)  
3. Process Payment (khi order confirmed – tức sau khi validate thành công).  

Sử dụng **Feign client** (dựa trên OpenAPI specs: member-service.yaml, product-service.yaml, payment-service.yaml).  
Mock bằng **in-memory stubs** (dev profile) và **WireMock** (test).  
Xử lý error handling (exceptions, timeouts, unavailability), logging, và resilience (Circuit Breaker với Resilience4j – bonus).  

**Quan trọng**: Không tích hợp external services vào PUT /api/orders/{id} (chỉ dùng để cancel order).  
Tập trung vào **createOrder** flow: save draft → validate → process payment → update status (PENDING → CONFIRMED/FAILED).

#### Phase 4a: General Setup for External Clients (Estimated: 5 minutes)
- Package: `com.sotatek.order.infrastructure.client`
- Dependency (build.gradle):
  ```gradle
  implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
  implementation 'io.github.resilience4j:resilience4j-spring-boot3'
  testImplementation 'com.github.tomakehurst:wiremock-jre8:2.35.0'
  ```
- Config Feign + profiles trong `application.yml` / `application-dev.yml`:
  ```yaml
  feign:
    client:
      config:
        default:
          connectTimeout: 5000
          readTimeout: 5000
  member:
    service:
      url: http://localhost:mock-member
  product:
    service:
      url: http://localhost:mock-product
  payment:
    service:
      url: http://localhost:mock-payment
  ```
- Enable Feign: Thêm `@EnableFeignClients` vào class main.
- Milestone: Build ok, no compile errors.

#### Phase 4b: Member Service Integration & Mock (Estimated: 10 minutes)
- **Yêu cầu đề**: Validate member exists & active.
- Feign Client (dựa spec yaml – điều chỉnh path/response theo yaml thực tế):
  ```java
  @FeignClient(name = "member-service", url = "${member.service.url:}")
  public interface MemberClient {
      @GetMapping("/members/{id}")
      MemberDto getMember(@PathVariable("id") Long id);
  }

  // DTO mẫu (tạo riêng hoặc dùng record)
  record MemberDto(Long id, boolean active) {}
  ```
- Mock:
    - Dev: Tạo `MockMemberClient` implements MemberClient (hardcode vài member active).
    - Test: WireMock stub (200 OK với active=true/false, 404 nếu invalid).
- Integrate trong `OrderService.createOrder()` (sau khi save draft):
  ```java
  MemberDto member = memberClient.getMember(request.getMemberId());
  if (member == null || !member.active()) {
      throw new InvalidMemberException("Member not found or inactive");
  }
  ```
- Resilience (bonus):
  ```java
  @CircuitBreaker(name = "memberService", fallbackMethod = "memberFallback")
  public MemberDto getMember(Long id) { ... }

  public MemberDto memberFallback(Long id, Throwable t) {
      log.error("Member service unavailable", t);
      throw new ServiceUnavailableException("Member service unavailable");
  }
  ```
- Test: Unit test service (Mockito mock client), integration test (WireMock).

#### Phase 4c: Product Service Integration & Mock (Estimated: 10 minutes)
- **Yêu cầu đề**: Verify product availability & stock.
- Feign Client:
  ```java
  @FeignClient(name = "product-service", url = "${product.service.url:}")
  public interface ProductClient {
      @GetMapping("/products/{id}")
      ProductDto getProduct(@PathVariable("id") Long id);
  }

  record ProductDto(Long id, int stock, BigDecimal price) {}
  ```
- Mock:
    - Dev: In-memory map (hardcode vài product với stock).
    - Test: WireMock stub (stock đủ/thiếu, 404 nếu not found).
- Integrate (sau member validate):
  ```java
  ProductDto product = productClient.getProduct(request.getProductId());
  if (product.stock() < request.getQuantity()) {
      throw new OutOfStockException("Insufficient stock for product " + request.getProductId());
  }
  // Optional: Cập nhật totalPrice = product.price() * quantity nếu spec yêu cầu
  ```
- Resilience (bonus): CircuitBreaker + @Retry.
- Test: Sad path (out of stock) → exception, không save order.

#### Phase 4d: Payment Service Integration & Mock (Estimated: 10 minutes)
- **Yêu cầu đề**: Process payment khi order confirmed (sau validate thành công).
- Logic flow:
    - Save order draft với status = PENDING
    - Validate member & product
    - Nếu OK → call payment
    - Success → update status = CONFIRMED / PAID
    - Fail → update status = FAILED + throw
- Feign Client:
  ```java
  @FeignClient(name = "payment-service", url = "${payment.service.url:}")
  public interface PaymentClient {
      @PostMapping("/payments")
      PaymentResponse process(@RequestBody PaymentRequest request);
  }

  record PaymentRequest(UUID orderId, BigDecimal amount) {}
  record PaymentResponse(boolean success, String transactionId) {}
  ```
- Mock:
    - Dev: Random success (70%) / fail (30%).
    - Test: WireMock stub (200 OK success/fail, 402 Payment Required).
- Integrate:
  ```java
  Order order = mapper.toEntity(request);
  order.setStatus(OrderStatus.PENDING);
  Order saved = repository.save(order);  // Save draft

  // Validate member & product...

  PaymentResponse payment = paymentClient.process(new PaymentRequest(saved.getId(), saved.getTotalPrice()));
  if (payment.success()) {
      saved.setStatus(OrderStatus.CONFIRMED);
  } else {
      saved.setStatus(OrderStatus.FAILED);
      throw new PaymentFailedException("Payment failed for order " + saved.getId());
  }
  repository.save(saved);
  ```
- Resilience: CircuitBreaker (critical), log transactionId.
- Test: Payment success → CONFIRMED; fail → FAILED + exception.

#### Phase 4e: Full Flow Test & Commit (Estimated: 5–10 minutes)
- End-to-end test: @SpringBootTest + WireMock server (stub tất cả 3 services).
    - Happy path: All OK → order CONFIRMED.
    - Sad paths: Member invalid, out of stock, payment fail → exception + status phù hợp.
- Profiles: `dev` → in-memory mocks; `test` → WireMock.
- Logging: Log mỗi call với correlation ID (MDC nếu bonus).
- Commit:
  ```
  feat: external services integration & mocking
  - Feign clients for Member, Product, Payment
  - In-memory mocks (dev) & WireMock (test)
  - Sequential integration in createOrder flow
  - Error handling & basic resilience (CircuitBreaker)
  - End-to-end tests for happy/sad paths
  ```

**Tổng kết Phase 4 – Senior Highlights**:
- Sequential calls theo đúng đề: Member → Product → Payment.
- Mocking strategy: In-memory cho dev nhanh, WireMock cho test realistic.
- Resilience & error handling: CircuitBreaker (bonus), meaningful exceptions.
- Không over-engineer: Chỉ tích hợp vào create flow, không đụng PUT.

**Tips**:
- Dựa chính xác path/response từ file yaml để define client/DTO.
- Nếu thời gian sát: Ưu tiên in-memory mock + basic error handling trước.
- Senior Mindset: Calls fault-tolerant, traceable logs, sẵn sàng cho Phase 5 tests.
```

Bạn có thể copy toàn bộ nội dung trên vào file `.md` (ví dụ: `phase-4.md`). Nếu cần chỉnh thêm chi tiết (ví dụ: path cụ thể từ yaml, hoặc code fallback đầy đủ), cứ báo mình nhé! Chúc bạn hoàn thành challenge xuất sắc! 🚀