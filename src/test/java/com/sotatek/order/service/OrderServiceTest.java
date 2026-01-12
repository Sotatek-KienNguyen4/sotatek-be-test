package com.sotatek.order.service;

import com.sotatek.order.dto.OrderRequestDTO;
import com.sotatek.order.dto.OrderResponseDTO;
import com.sotatek.order.entity.Order;
import com.sotatek.order.entity.OrderStatus;
import com.sotatek.order.exception.OrderNotFoundException;
import com.sotatek.order.mapper.OrderMapper;
import com.sotatek.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository repository;

    @Mock
    private OrderMapper mapper;

    @InjectMocks
    private OrderService service;

    @Test
    void createOrderShouldSaveAndReturnDTO() {
        // Given
        OrderRequestDTO request = OrderRequestDTO.builder()
                .memberId(1L)
                .totalPrice(BigDecimal.TEN)
                .build();
        Order order = Order.builder().id(UUID.randomUUID()).build();
        OrderResponseDTO response = OrderResponseDTO.builder().id(order.getId()).build();

        when(mapper.toEntity(request)).thenReturn(order);
        when(repository.save(order)).thenReturn(order);
        when(mapper.toResponseDTO(order)).thenReturn(response);

        // When
        OrderResponseDTO result = service.createOrder(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(order.getId());
        verify(repository).save(order);
    }

    @Test
    void getOrderByIdShouldThrowNotFound() {
        // Given
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(OrderNotFoundException.class, () -> service.getOrderById(id));
    }
}
