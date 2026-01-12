Dưới đây là phiên bản **lược gọn** của Phase 3, đã bỏ bớt các đoạn giải thích dài dòng, lý do senior, trade-off note, pitfall, checklist, milestone, và tổng kết không cần thiết. Giữ lại cấu trúc, code mẫu và nội dung cốt lõi để bạn dễ copy-paste vào file Markdown.

```markdown
### Phase 3: Implement Core REST APIs (Estimated: 40–50 minutes)

#### Phase 3a: Define DTOs and Mappers (Estimated: 7–9 minutes)
- Package: `com.example.orderservice.adapter.dto`
- `OrderRequestDTO`:
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderRequestDTO {
    @NotNull private UUID memberId;
    @NotNull private UUID productId;
    @Min(1) private Integer quantity;
    @NotNull @Min(0) private BigDecimal totalPrice;
    private OrderStatus status;
}
```
- `OrderResponseDTO`:
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderResponseDTO {
    private UUID id;
    private UUID memberId;
    private UUID productId;
    private Integer quantity;
    private BigDecimal totalPrice;
    private OrderStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
```
- Dependency MapStruct (build.gradle):
```gradle
implementation 'org.mapstruct:mapstruct:1.5.5.Final'
annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'
```
- `OrderMapper`:
```java
@Mapper(componentModel = "spring")
public interface OrderMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Order toEntity(OrderRequestDTO dto);

    OrderResponseDTO toResponseDTO(Order order);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateFromDto(OrderRequestDTO dto, @MappingTarget Order entity);
}
```

#### Phase 3b: Implement Service Layer (Estimated: 10–12 minutes)
- Package: `com.example.orderservice.application.service`
- `OrderService`:
```java
@Service @Transactional @RequiredArgsConstructor @Slf4j
public class OrderService {
    private final OrderRepository repository;
    private final OrderMapper mapper;

    public OrderResponseDTO createOrder(OrderRequestDTO request) {
        Order order = mapper.toEntity(request);
        if (request.getTotalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total price must be positive");
        }
        Order saved = repository.save(order);
        log.info("Created order with ID: {}", saved.getId());
        return mapper.toResponseDTO(saved);
    }

    public OrderResponseDTO getOrderById(UUID id) {
        Order order = repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + id));
        return mapper.toResponseDTO(order);
    }

    public Page<OrderResponseDTO> listOrders(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toResponseDTO);
    }

    public OrderResponseDTO updateOrder(UUID id, OrderRequestDTO request) {
        Order existing = repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + id));
        mapper.updateFromDto(request, existing);
        Order updated = repository.save(existing);
        log.info("Updated order ID: {}", id);
        return mapper.toResponseDTO(updated);
    }

    public void deleteOrder(UUID id) {
        Order order = repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + id));
        order.setStatus(OrderStatus.CANCELLED);
        repository.save(order);
        log.info("Cancelled order ID: {}", id);
    }
}
```

#### Phase 3c: Implement Controller Layer (Estimated: 8–10 minutes)
- Package: `com.example.orderservice.adapter.controller`
- `OrderController`:
```java
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order API")
public class OrderController {
    private final OrderService service;

    @PostMapping
    @Operation(summary = "Create a new order")
    public ResponseEntity<OrderResponseDTO> create(@Valid @RequestBody OrderRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createOrder(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<OrderResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getOrderById(id));
    }

    @GetMapping
    @Operation(summary = "List orders with pagination")
    public ResponseEntity<Page<OrderResponseDTO>> list(
            @PageableDefault(page = 0, size = 10, sort = "createdAt,desc") Pageable pageable) {
        return ResponseEntity.ok(service.listOrders(pageable));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing order")
    public ResponseEntity<OrderResponseDTO> update(@PathVariable UUID id, @Valid @RequestBody OrderRequestDTO request) {
        return ResponseEntity.ok(service.updateOrder(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel an order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deleteOrder(id);
    }
}
```
- Dependency Springdoc (build.gradle):
```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'
```

#### Phase 3d: Error Handling (Estimated: 7–9 minutes)
- Custom exception:
```java
@ResponseStatus(HttpStatus.NOT_FOUND)
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String message) { super(message); }
}
```
- `GlobalExceptionHandler`:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<String> handleNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class})
    public ResponseEntity<String> handleConcurrency(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Order updated by another transaction");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error");
    }
}
```

#### Phase 3e: Unit Tests & Commit (Estimated: 8–10 minutes)
- Service test (Mockito):
```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock OrderRepository repository;
    @Mock OrderMapper mapper;
    @InjectMocks OrderService service;

    @Test
    void createOrderShouldSaveAndReturnDTO() {
        // ... (giữ sample test create success)
    }

    @Test
    void getOrderByIdShouldThrowNotFound() {
        // ... (giữ sample test not found)
    }
}
```
- Controller test (@WebMvcTest):
```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean OrderService service;
    @Autowired ObjectMapper objectMapper;

    @Test
    void createOrderShouldReturn201() throws Exception {
        // ... (giữ sample test create)
    }
}
```
- Commit message:
```
feat: implement core REST APIs
- DTOs, MapStruct mapper
- OrderService with CRUD logic
- OrderController with endpoints & Swagger
- Global error handling
- Unit tests for service & controller
```

**Lưu ý khi dùng trong file MD**:
- Giữ code block trong ```java và ```gradle
- Nếu file quá dài, bạn có thể tách mỗi sub-phase thành heading cấp 4 (####)
```

Phiên bản này ngắn gọn, tập trung vào code và cấu trúc, phù hợp để lưu vào file plan. Nếu cần rút gọn thêm hoặc điều chỉnh phần nào, cứ báo mình nhé!