### Tổng Quan Về Phase 1: Project Setup & Initialization
Phase 1 là giai đoạn khởi tạo nền tảng cho toàn bộ dự án, tập trung vào việc xây dựng một bộ khung Spring Boot cơ bản, sạch sẽ và chạy được ngay. Thời gian ước tính 30-45 phút để tránh mất thời gian vào boilerplate code sau này. Mục tiêu chính là thiết lập môi trường phát triển, cấu hình công cụ cần thiết, và đảm bảo ứng dụng có thể build & run mà không lỗi.

Ở phase này, chúng ta sẽ:
- Khởi tạo repository Git mới.
- Tạo dự án Spring Boot với Gradle, thêm các dependency cơ bản (web, JPA, validation, Lombok, H2).
- Cấu hình file `application.yml` cho database, logging, và server.
- Xây dựng cấu trúc package chuẩn theo layer (controller, service, etc.).
- Thêm một endpoint health check đơn giản để kiểm tra.
- Build, run, và commit lần đầu.

Sau phase này, bạn sẽ có một ứng dụng Spring Boot chạy trên localhost:8080 với H2 database sẵn sàng, làm nền tảng cho các phase sau. Đây là phase "nền móng" nên ưu tiên tốc độ và tính ổn định, tránh over-engineering.

Bây giờ, chúng ta đi sâu vào **phát triển chi tiết và đầy đủ cho Phase 1**. Tôi sẽ mô tả từng bước một cách cụ thể, kèm code mẫu, lệnh terminal, và lưu ý senior-level (như clean code, best practices). Bạn có thể copy-paste trực tiếp để thực hiện. Giả sử bạn dùng IDE như IntelliJ (khuyến nghị cho Java), nhưng lệnh Gradle sẽ hoạt động độc lập.

### Phát Triển Chi Tiết Phase 1

#### Bước 1: Khởi Tạo Repository (5-10 phút)
Mục tiêu: Tạo một repo sạch, riêng biệt để submit, tránh copy toàn bộ từ mẫu.

1. **Clone repo mẫu để tham khảo (nếu có)**:
    - Nếu bài test cung cấp repo minimal, clone nó để xem cấu trúc ban đầu (không copy code chính).
      ```
      git clone <url-repo-mau-cua-bai-test>
      ```
    - Xem qua `build.gradle`, `application.properties` (nếu có), rồi xóa repo này sau khi tham khảo.

2. **Tạo repo mới**:
    - Tạo thư mục dự án:
      ```
      mkdir order-microservice
      cd order-microservice
      git init
      ```
    - Tạo `.gitignore` (copy từ https://gitignore.io/?templates=java,gradle,intellij hoặc nội dung cơ bản dưới):
      ```
      # Java
      *.class
      *.jar
      *.war
      *.ear
      target/
      build/
 
      # Gradle
      .gradle/
      gradlew
      gradlew.bat
 
      # IDE
      .idea/
      *.iml
      out/
 
      # Logs
      *.log
      ```
      Lưu file `.gitignore` ở root.

3. **Tạo README.md ban đầu**:
    - Tạo file `README.md` với nội dung sơ bộ (sẽ bổ sung sau):
      ```
      # Order Microservice
 
      ## Overview
      Microservice quản lý đơn hàng, tích hợp với Member, Product, Payment services.
 
      ## Tech Stack
      - Java 17
      - Spring Boot 3.x
      - Gradle
      - Postgress Database
 
      ## How to Run
      ./gradlew bootRun
      ```
    - Commit ban đầu:
      ```
      git add .
      git commit -m "Initial commit: Setup gitignore and README"
      ```

Lưu ý: Nếu submit lên GitHub, tạo repo private và invite interviewer. Đảm bảo repo của bạn là original work.

#### Bước 2: Tạo Dự Án Spring Boot Với Gradle (5 phút)
Sử dụng Spring Initializr để generate nhanh, đảm bảo phiên bản mới nhất.

1. **Generate qua web (khuyến nghị)**:
    - Truy cập https://start.spring.io/.
    - Cấu hình:
        - Project: Gradle Project.
        - Language: Java.
        - Spring Boot: 3.3.5 (hoặc mới nhất ổn định).
        - Group: com.example.
        - Artifact: orderservice.
        - Name: OrderService.
        - Description: Order Microservice for assessment.
        - Package name: com.example.orderservice.
        - Packaging: Jar.
        - Java: 17.
        - Dependencies: Thêm Spring Web, Spring Data JPA, Spring Boot DevTools, Lombok, Validation, H2 Database.
    - Nhấn GENERATE → tải ZIP → giải nén vào thư mục `order-microservice`.
    - Di chuyển file ra root nếu cần: `mv mvnw* src build.gradle* .*` (nếu có thư mục con).

2. **Hoặc generate qua curl (nếu mạng chậm)**:
   ```
   curl https://start.spring.io/starter.tgz \
     -d dependencies=web,data-jpa,h2,lombok,validation,devtools \
     -d type=gradle-project \
     -d language=java \
     -d javaVersion=17 \
     -d bootVersion=3.3.5 \
     -d group=com.example \
     -d artifact=orderservice \
     -d name=OrderService \
     -d packageName=com.example.orderservice \
     -o orderservice.tgz
   tar -xzvf orderservice.tgz
   mv * ..
   cd ..
   rm -rf orderservice.tgz
   ```

Lưu ý senior: Sử dụng Lombok để giảm boilerplate (getters/setters), DevTools để hot-reload khi dev, giúp tiết kiệm thời gian.

#### Bước 3: Cập Nhật build.gradle (5 phút)
Mở `build.gradle` và đảm bảo các dependency đầy đủ. Thêm Feign nếu quyết định dùng (dễ mock external services).

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
    // Thêm nếu dùng Feign
    id 'org.springframework.cloud' version '4.1.5'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    // Feign cho external clients (bonus cho integration)
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    runtimeOnly 'com.h2database:h2'
    // Uncomment cho PostgreSQL sau
    // runtimeOnly 'org.postgresql:postgresql'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

- Chạy `./gradlew dependencies` để kiểm tra dependency tree, đảm bảo không conflict.

Lưu ý: Giữ version nhất quán để tránh lỗi build. Nếu dùng Feign, nó sẽ hữu ích ở Phase 4.

#### Bước 4: Cấu Hình application.yml (5 phút)
Chuyển từ `application.properties` sang `application.yml` (dễ đọc hơn). Xóa file cũ nếu có, tạo mới ở `src/main/resources/application.yml`.

```yaml
server:
  port: 8080
  error:
    include-message: always  # Hiển thị message lỗi chi tiết cho dev

spring:
  application:
    name: order-service
  datasource:
    url: jdbc:h2:mem:ordersdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password: 
  jpa:
    hibernate:
      ddl-auto: create-drop  # Auto tạo table ở dev, đổi sang 'validate' ở prod
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
        format_sql: true
  h2:
    console:
      enabled: true
      path: /h2-console  # Truy cập http://localhost:8080/h2-console

logging:
  level:
    root: INFO
    org.springframework.web: DEBUG
    com.example.orderservice: DEBUG
```

Lưu ý: `create-drop` phù hợp cho test nhanh, nhưng ở production dùng migration tools như Flyway (bonus ở Phase 6). Enable H2 console để debug database dễ dàng.

#### Bước 5: Xây Dựng Cấu Trúc Package (2-3 phút)
Trong `src/main/java/com.example.orderservice`, tạo các subpackage (dùng IDE hoặc mkdir):

- config (cho beans, security nếu cần)
- controller
- dto (request/response objects)
- entity (JPA entities)
- exception (custom exceptions & handlers)
- repository
- service
- client (cho Feign clients gọi external services)

Class chính `OrderServiceApplication.java` giữ nguyên với `@SpringBootApplication`.

Lưu ý senior: Áp dụng package-by-layer để tuân thủ SOLID, dễ maintain. Tránh package-by-feature nếu dự án nhỏ.

#### Bước 6: Thêm Health Check Endpoint (3 phút)
Tạo file `HealthController.java` ở package controller:

```java
package com.example.orderservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Order Service is UP and Running!");
    }
}
```

Lưu ý: Đây là cách đơn giản để verify app chạy. Ở senior level, có thể thêm Actuator sau cho health checks chuyên sâu (bonus).

#### Bước 7: Build, Run Và Kiểm Tra (5 phút)
1. Build:
   ```
   ./gradlew clean build
   ```
    - Nếu lỗi, check Java version (phải 17), hoặc gradle wrapper.

2. Run:
   ```
   ./gradlew bootRun
   ```
    - Kiểm tra log console: No errors, app starts on port 8080.

3. Test:
    - Truy cập http://localhost:8080/health → Phải trả về "Order Service is UP and Running!" với status 200.
    - Truy cập http://localhost:8080/h2-console → Login với JDBC URL: jdbc:h2:mem:ordersdb, user: sa, pass: empty. Database phải connect được (bảng trống lúc này).

Nếu lỗi: Check firewall port 8080, hoặc restart IDE.

#### Bước 8: Commit Và Kết Thúc Phase 1 (2 phút)
```
git add .
git commit -m "Phase 1 complete: Spring Boot setup, configs, health endpoint"
```

**Milestone Đạt Được**: Ứng dụng chạy ổn định, sẵn sàng cho Phase 2 (database modeling). Nếu hết 45 phút, bạn đã có nền tảng vững chắc, có thể demo health check cho interviewer nếu cần.

Nếu bạn đã hoàn thành Phase 1 theo hướng dẫn này, hãy cho tôi biết để chúng ta chuyển sang Phase 2 nhé! Hoặc nếu cần chỉnh sửa gì ở Phase 1.