package com.sotatek.order.service;

import com.sotatek.order.dto.OrderRequestDTO;
import com.sotatek.order.dto.OrderResponseDTO;
import com.sotatek.order.entity.Order;
import com.sotatek.order.entity.OrderStatus;
import com.sotatek.order.exception.InvalidMemberException;
import com.sotatek.order.exception.OrderNotFoundException;
import com.sotatek.order.exception.OutOfStockException;
import com.sotatek.order.exception.PaymentFailedException;
import com.sotatek.order.infrastructure.client.MemberClient;
import com.sotatek.order.infrastructure.client.PaymentClient;
import com.sotatek.order.infrastructure.client.ProductClient;
import com.sotatek.order.infrastructure.client.dto.MemberResponse;
import com.sotatek.order.infrastructure.client.dto.PaymentRequest;
import com.sotatek.order.infrastructure.client.dto.PaymentResponse;
import com.sotatek.order.infrastructure.client.dto.ProductResponse;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository repository;

    @Mock
    private OrderMapper mapper;

    @Mock
    private MemberClient memberClient;

    @Mock
    private ProductClient productClient;

    @Mock
    private PaymentClient paymentClient;

    @InjectMocks
    private OrderService service;

    @Test
    void createOrder_HappyPath_ShouldSaveAndReturnDTO() {
        // Given
        Long memberId = 1L;
        Long productId = 101L;
        int quantity = 2;
        BigDecimal price = BigDecimal.valueOf(100);
        BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(quantity));

        OrderRequestDTO request = OrderRequestDTO.builder()
                .memberId(memberId)
                .productId(productId)
                .quantity(quantity)
                .totalPrice(totalPrice)
                .build();

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .totalPrice(totalPrice)
                .build();

        OrderResponseDTO response = OrderResponseDTO.builder().id(order.getId()).status(OrderStatus.CONFIRMED).build();

        // 1. Mock Member
        when(memberClient.getMember(memberId)).thenReturn(new MemberResponse(memberId, true, true));

        // 2. Mock Product
        when(productClient.checkStock(productId, quantity))
                .thenReturn(new ProductResponse(productId, true, 100, price));

        // 3. Mock Mapper & Save PENDING
        when(mapper.toEntity(request)).thenReturn(order);
        when(repository.save(any(Order.class))).thenReturn(order);

        // 4. Mock Payment
        when(paymentClient.processPayment(any(PaymentRequest.class)))
                .thenReturn(new PaymentResponse("TX-123", true, "Success"));

        // 5. Mock Mapper Final Response
        when(mapper.toResponseDTO(any(Order.class))).thenReturn(response);

        // When
        OrderResponseDTO result = service.createOrder(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // Verify interactions
        verify(memberClient).getMember(memberId);
        verify(productClient).checkStock(productId, quantity);
        verify(paymentClient).processPayment(any(PaymentRequest.class));
        verify(repository, times(2)).save(any(Order.class)); // 1 for pending, 1 for confirmed
    }

    @Test
    void createOrder_InvalidMember_ShouldThrowException() {
        // Given
        Long memberId = 99L;
        OrderRequestDTO request = OrderRequestDTO.builder().memberId(memberId).build();

        when(memberClient.getMember(memberId)).thenReturn(new MemberResponse(memberId, false, false));

        // When/Then
        assertThrows(InvalidMemberException.class, () -> service.createOrder(request));

        verify(productClient, never()).checkStock(any(), anyInt());
        verify(repository, never()).save(any());
    }

    @Test
    void createOrder_OutOfStock_ShouldThrowException() {
        // Given
        Long memberId = 1L;
        Long productId = 101L;
        int quantity = 10;
        OrderRequestDTO request = OrderRequestDTO.builder()
                .memberId(memberId)
                .productId(productId)
                .quantity(quantity)
                .build();

        when(memberClient.getMember(memberId)).thenReturn(new MemberResponse(memberId, true, true));
        when(productClient.checkStock(productId, quantity))
                .thenReturn(new ProductResponse(productId, true, 5, BigDecimal.TEN)); // Stock 5 < 10

        // When/Then
        assertThrows(OutOfStockException.class, () -> service.createOrder(request));

        verify(repository, never()).save(any());
    }

    @Test
    void createOrder_PaymentFailed_ShouldThrowException() {
        // Given
        Long memberId = 1L;
        Long productId = 101L;
        int quantity = 1;
        BigDecimal price = BigDecimal.TEN;

        OrderRequestDTO request = OrderRequestDTO.builder()
                .memberId(memberId)
                .productId(productId)
                .quantity(quantity)
                .totalPrice(price)
                .build();

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .totalPrice(price)
                .build();

        when(memberClient.getMember(memberId)).thenReturn(new MemberResponse(memberId, true, true));
        when(productClient.checkStock(productId, quantity))
                .thenReturn(new ProductResponse(productId, true, 100, price));
        when(mapper.toEntity(request)).thenReturn(order);
        when(repository.save(any(Order.class))).thenReturn(order);

        // Mock Payment Failure
        when(paymentClient.processPayment(any(PaymentRequest.class)))
                .thenReturn(new PaymentResponse(null, false, "Insufficient funds"));

        // When/Then
        assertThrows(PaymentFailedException.class, () -> service.createOrder(request));

        // Ensure pending order was saved (and transaction rollback would handle
        // deletion in real integration)
        verify(repository, times(1)).save(order);
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
