```markdown
### Phase 4a: General Setup for External Clients (Estimated: 5–7 minutes)

**Mục tiêu**:  
Xây dựng nền tảng chung vững chắc cho việc gọi các external services (Member, Product, Payment) bằng Feign Client. Tích hợp resilience (circuit breaker + retry), custom error handling, mocking strategy (WireMock cho test, in-memory stub cho dev), và cấu hình profiles để switch môi trường dễ dàng (dev/mock/test/prod).  

Phần này đảm bảo fault-tolerance, observability, traceability và testability – những yếu tố senior Java luôn chú trọng trong microservices để xử lý real-world failures (timeout, unavailability, 4xx/5xx responses).

#### Bước 1: Update & Verify Dependencies trong build.gradle
Cập nhật các phiên bản mới nhất tính đến 2026 (Spring Boot 4.0.x compatible, Resilience4j 2.x stable, WireMock 3.13.x stable).  
Chạy `./gradlew dependencies` để kiểm tra conflict, sau đó `./gradlew build` để verify.

```gradle
// Feign + Spring Cloud (compatible với Spring Boot 4.0.x)
implementation 'org.springframework.cloud:spring-cloud-starter-openfeign:4.1.5'  // hoặc phiên bản mới nhất 2025/2026

// Resilience4j cho circuit breaker, retry, rate limiter (senior: fault tolerance & graceful degradation)
implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'      // stable 2025-2026
implementation 'io.github.resilience4j:resilience4j-feign:2.2.0'             // tích hợp trực tiếp với Feign

// WireMock cho integration test (mock HTTP realistic theo OpenAPI spec)
testImplementation 'org.wiremock:wiremock-jre8-standalone:3.13.2'            // latest stable 2026
testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-stub-runner'  // optional nếu muốn contract testing
```

**Lý do senior**:
- Resilience4j-Feign → declarative circuit breaker/retry trên Feign, lightweight và Spring-native.
- WireMock standalone → hỗ trợ stub full HTTP (status, headers, delays, faults, JSON body) khớp với OpenAPI spec.
- Giữ lightweight, không thêm lib thừa để tập trung vào core integration.

#### Bước 2: Enable Feign Clients & Resilience4j trong Main Application Class
```java
package com.sotatek.order;  // thay bằng package thực tế của bạn

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.sotatek.order.infrastructure.client")
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
```

**Lưu ý**: Nếu dùng Spring Boot 4.0+, Resilience4j auto-configuration qua starter. Không cần thêm annotation riêng.

#### Bước 3: Cấu hình Feign, Resilience4j và External URLs trong application.yml
Tạo cấu hình chi tiết, hỗ trợ profiles, custom error handling và observability.

```yaml
# application.yml (common defaults)
feign:
  client:
    config:
      default:
        connectTimeout: 3000   # 3 giây - nhanh cho dev/test
        readTimeout: 8000      # 8 giây
        loggerLevel: FULL      # debug calls chi tiết (dev), chuyển BASIC ở prod để giảm log
        # Custom error decoder để map FeignException thành meaningful exceptions
        errorDecoder: com.sotatek.order.infrastructure.client.CustomFeignErrorDecoder

# Resilience4j config (circuit breaker + retry riêng cho từng service)
resilience4j:
  circuitbreaker:
    instances:
      memberService:
        registerHealthIndicator: true           # expose /actuator/health
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10000          # 10 giây
        permittedNumberOfCallsInHalfOpenState: 3
      productService:
        slidingWindowSize: 10
        failureRateThreshold: 50
      paymentService:  # Critical hơn → threshold chặt chẽ
        slidingWindowSize: 5
        failureRateThreshold: 60
        waitDurationInOpenState: 20000
  retry:
    instances:
      default:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2

# External service URLs (placeholders, override theo profile)
member:
  service:
    url: http://localhost:8081
product:
  service:
    url: http://localhost:8082
payment:
  service:
    url: http://localhost:8083

# application-dev.yml (dev/mock/in-memory)
member.service.url: http://localhost:8081/mock  # hoặc dùng in-memory stub
product.service.url: http://localhost:8082/mock
payment.service.url: http://localhost:8083/mock

# application-test.yml (WireMock cho integration test)
member.service.url: http://localhost:${wiremock.server.port}
product.service.url: http://localhost:${wiremock.server.port}
payment.service.url: http://localhost:${wiremock.server.port}

# application-prod.yml (real env - ví dụ Kubernetes)
# member.service.url: http://member-service.default.svc.cluster.local
```

**Lý do senior**:
- Custom error decoder → chuyển FeignException thành domain exception (e.g., ServiceUnavailableException).
- Resilience4j health indicator → tích hợp Actuator, expose circuit state (UP/DOWN) ở /actuator/health.
- Exponential backoff retry → xử lý transient failures hiệu quả.
- Profile override → dễ switch mock/real, tránh hardcode.

#### Bước 4: Tạo Custom Feign Error Decoder (Senior touch – handle errors graceful)
Tạo class trong `com.sotatek.order.infrastructure.client`:

```java
package com.sotatek.order.infrastructure.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CustomFeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        HttpStatus status = HttpStatus.resolve(response.status());

        if (status == HttpStatus.SERVICE_UNAVAILABLE || status == HttpStatus.GATEWAY_TIMEOUT) {
            return new ServiceUnavailableException("External service unavailable: " + methodKey);
        }

        if (status != null && status.is4xxClientError()) {
            // Có thể parse body để lấy message chi tiết từ external nếu spec có
            return new ExternalServiceClientException("Client error from " + methodKey + ": " + response.status());
        }

        if (status != null && status.is5xxServerError()) {
            return new ExternalServiceServerException("Server error from " + methodKey + ": " + response.status());
        }

        return defaultDecoder.decode(methodKey, response);
    }
}
```

**Tạo các exception tương ứng** (trong package exception):
```java
public class ServiceUnavailableException extends RuntimeException { ... }
public class ExternalServiceClientException extends RuntimeException { ... }
public class ExternalServiceServerException extends RuntimeException { ... }
```

**Lý do senior**: Không để default FeignException (ít meaningful), mà map thành domain exception → dễ handle ở service layer và trả response client chuẩn (e.g., 503 Service Unavailable).

#### Bước 5: Tạo Package Structure & Smoke Validation
- Package chính: `com.sotatek.order.infrastructure.client`
    - Đặt tất cả Feign interfaces, config, decoder, fallback factories ở đây.
- Smoke check:
    - Run `./gradlew bootRun` → kiểm tra log không lỗi Feign initialization.
    - Access `/actuator/health` (nếu có Resilience4j health indicator) → verify circuit breakers UP.

#### Bước 6: Commit & README Update
Commit message:
```
chore: foundation for external services integration

- Feign clients setup with custom error decoder
- Resilience4j circuit breaker & retry configuration
- Profile-based URLs, timeouts, and mocking support (WireMock)
- Health indicator integration for observability
```

Cập nhật README.md:
```markdown
## External Services Integration
- Sử dụng **Feign** với **Resilience4j** để gọi Member, Product, Payment services.
- **Fault tolerance**: Circuit breaker + exponential retry cho transient failures.
- **Mocking**: WireMock trong integration test, in-memory stubs trong dev.
- **Error handling**: Custom Feign error decoder map thành domain exceptions.
- **Observability**: Circuit breaker health indicator expose qua Actuator.
```

**Milestone Phase 4a**:
- Dependencies resolve, build & boot thành công.
- Feign enabled, configs override theo profile.
- Custom decoder + Resilience4j sẵn sàng.
- Logs Feign hoạt động khi gọi (sẽ thấy ở phase sau).
- Sẵn sàng implement client cụ thể ở Phase 4b, 4c, 4d.

**Tips senior thực hiện nhanh**:
- Nếu thời gian sát nút → skip custom decoder và Resilience4j chi tiết → dùng default FeignException + basic circuit breaker.
- Debug nhanh: Thêm tạm `logging.level.feign=DEBUG` vào application-dev.yml.
- Observability bonus: Resilience4j tự động expose metrics tại `/actuator/metrics/resilience4j.circuitbreaker.calls`.

Phần này giờ đã **đầy đủ, production-grade và xứng tầm senior Java**: fault-tolerant, observable, testable, clean config và decision documented rõ ràng. Bạn copy toàn bộ nội dung trên vào file `.md` là hoàn thiện Phase 4a.
```