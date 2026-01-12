package com.sotatek.order.controller;

import com.sotatek.order.dto.OrderRequestDTO;
import com.sotatek.order.dto.OrderResponseDTO;
import com.sotatek.order.dto.OrderUpdateDTO;
import com.sotatek.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order API", description = "APIs for managing orders")
public class OrderController {

    private final OrderService service;

    @PostMapping
    @Operation(summary = "Create a new order", description = "Creates a new order")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    public ResponseEntity<OrderResponseDTO> create(@Valid @RequestBody OrderRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createOrder(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID", description = "Retrieves details of a specific order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getOrderById(id));
    }

    @GetMapping
    @Operation(summary = "List orders with pagination", description = "Lists all orders with pagination support")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "List of orders"))
    public ResponseEntity<Page<OrderResponseDTO>> list(
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.listOrders(pageable));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update / Cancel an order", description = "Updates order details or cancels by setting status to CANCELLED. Partial update supported.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order updated successfully"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    public ResponseEntity<OrderResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody OrderUpdateDTO updateRequest) { // <-- đổi sang OrderUpdateDTO
        return ResponseEntity.ok(service.updateOrder(id, updateRequest));
    }
}