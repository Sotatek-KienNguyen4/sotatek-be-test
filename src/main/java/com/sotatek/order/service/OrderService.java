package com.sotatek.order.service;

import com.sotatek.order.dto.OrderRequestDTO;
import com.sotatek.order.dto.OrderResponseDTO;
import com.sotatek.order.dto.OrderUpdateDTO;
import com.sotatek.order.entity.Order;
import com.sotatek.order.entity.OrderStatus;
import com.sotatek.order.exception.OrderNotFoundException;
import com.sotatek.order.mapper.OrderMapper;
import com.sotatek.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import com.sotatek.order.exception.InvalidMemberException;
import com.sotatek.order.exception.OutOfStockException;
import com.sotatek.order.exception.PaymentFailedException;
import com.sotatek.order.infrastructure.client.MemberClient;
import com.sotatek.order.infrastructure.client.PaymentClient;
import com.sotatek.order.infrastructure.client.ProductClient;
import com.sotatek.order.infrastructure.client.dto.MemberResponse;
import com.sotatek.order.infrastructure.client.dto.PaymentRequest;
import com.sotatek.order.infrastructure.client.dto.PaymentResponse;
import com.sotatek.order.infrastructure.client.dto.ProductResponse;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository repository;
    private final OrderMapper mapper;
    private final MemberClient memberClient;
    private final ProductClient productClient;
    private final PaymentClient paymentClient;

    public OrderResponseDTO createOrder(OrderRequestDTO request) {
        log.info("Creating order for member: {}", request.getMemberId());

        // 1. Validate Member
        MemberResponse member = memberClient.getMember(request.getMemberId());
        if (!member.isExists() || !member.isActive()) {
            throw new InvalidMemberException("Member invalid or inactive: " + request.getMemberId());
        }

        // 2. Validate Product & Stock
        ProductResponse product = productClient.checkStock(request.getProductId(), request.getQuantity());
        if (!product.isAvailable() || product.getStock() < request.getQuantity()) {
            log.warn("Insufficient stock for product {} (requested: {}, available: {})",
                    request.getProductId(), request.getQuantity(), product.getStock());
            throw new OutOfStockException("Insufficient stock for product " + request.getProductId());
        }

        // Optional: Sync price
        if (product.getPrice() != null) {
            request.setTotalPrice(product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
        }

        // 3. Validate Price
        if (request.getTotalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total price must be > 0");
        }

        // 4. Save Order (PENDING)
        Order order = mapper.toEntity(request);
        order.setStatus(OrderStatus.PENDING);
        Order saved = repository.save(order);
        log.info("Created pending order with ID: {}", saved.getId());

        // 5. Process Payment (Synchronous)
        try {
            PaymentResponse payment = paymentClient.processPayment(PaymentRequest.builder()
                    .orderId(saved.getId())
                    .amount(saved.getTotalPrice())
                    .build());

            if (payment.isSuccess()) {
                log.info("Payment successful for order {}", saved.getId());
                saved.setStatus(OrderStatus.CONFIRMED);
                saved = repository.save(saved);
            } else {
                log.warn("Payment failed for order {}: {}", saved.getId(), payment.getMessage());
                throw new PaymentFailedException("Payment failed: " + payment.getMessage());
            }
        } catch (Exception e) {
            log.error("Payment error for order {}", saved.getId(), e);
            throw new PaymentFailedException("Payment error: " + e.getMessage());
        }

        return mapper.toResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponseDTO getOrderById(UUID id) {
        Order order = repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + id));
        return mapper.toResponseDTO(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponseDTO> listOrders(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toResponseDTO);
    }

    public OrderResponseDTO updateOrder(UUID id, OrderUpdateDTO updateRequest) {
        Order existing = repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + id));

        // Partial update: chỉ thay đổi field nào được gửi lên
        if (updateRequest.getMemberId() != null) {
            existing.setMemberId(updateRequest.getMemberId());
        }
        if (updateRequest.getTotalPrice() != null) {
            existing.setTotalPrice(updateRequest.getTotalPrice());
        }
        if (updateRequest.getStatus() != null) {
            // Có thể thêm business rule: chỉ cho phép cancel nếu đang PENDING hoặc
            // CONFIRMED
            if (updateRequest.getStatus() == OrderStatus.CANCELLED &&
                    existing.getStatus() != OrderStatus.PENDING &&
                    existing.getStatus() != OrderStatus.CONFIRMED) {
                throw new IllegalStateException("Cannot cancel order in current status: " + existing.getStatus());
            }
            existing.setStatus(updateRequest.getStatus());
        }

        // Lưu ý: Không update orderItems ở đây (nếu cần update items → cần endpoint
        // riêng hoặc logic phức tạp hơn)

        Order updated = repository.save(existing);
        return mapper.toResponseDTO(updated);
    }
}
