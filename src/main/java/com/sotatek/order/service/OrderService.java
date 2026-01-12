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

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository repository;
    private final OrderMapper mapper;

    public OrderResponseDTO createOrder(OrderRequestDTO request) {
        if (request.getTotalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total price must be positive");
        }

        Order order = mapper.toEntity(request);

        // Handle initial status if not provided
        if (order.getStatus() == null) {
            order.setStatus(OrderStatus.PENDING);
        }

        Order saved = repository.save(order);
        log.info("Created order with ID: {}", saved.getId());
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
            // Có thể thêm business rule: chỉ cho phép cancel nếu đang PENDING hoặc CONFIRMED
            if (updateRequest.getStatus() == OrderStatus.CANCELLED &&
                    existing.getStatus() != OrderStatus.PENDING &&
                    existing.getStatus() != OrderStatus.CONFIRMED) {
                throw new IllegalStateException("Cannot cancel order in current status: " + existing.getStatus());
            }
            existing.setStatus(updateRequest.getStatus());
        }

        // Lưu ý: Không update orderItems ở đây (nếu cần update items → cần endpoint riêng hoặc logic phức tạp hơn)

        Order updated = repository.save(existing);
        return mapper.toResponseDTO(updated);
    }
}
