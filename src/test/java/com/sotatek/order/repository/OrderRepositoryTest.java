package com.sotatek.order.repository;

import com.sotatek.order.entity.Order;
import com.sotatek.order.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void shouldFindOrdersByStatus() {
        // Given
        Order order = Order.builder()
                .memberId(1L)
                .totalPrice(BigDecimal.valueOf(100.00))
                .status(OrderStatus.PENDING)
                .build();
        orderRepository.save(order);

        // When
        Page<Order> foundOrders = orderRepository.findByStatus(OrderStatus.PENDING, PageRequest.of(0, 10));

        // Then
        assertThat(foundOrders).isNotEmpty();
        assertThat(foundOrders.getContent().get(0).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void shouldAuditOrder() {
        // Given
        Order order = Order.builder()
                .memberId(1L)
                .totalPrice(BigDecimal.valueOf(50.00))
                .status(OrderStatus.CONFIRMED)
                .build();

        // When
        Order savedOrder = orderRepository.save(order);

        // Then
        assertThat(savedOrder.getCreatedAt()).isNotNull();
        assertThat(savedOrder.getUpdatedAt()).isNotNull();
    }
}
