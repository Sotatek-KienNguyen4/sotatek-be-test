package com.sotatek.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sotatek.order.dto.OrderRequestDTO;
import com.sotatek.order.dto.OrderResponseDTO;
import com.sotatek.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createOrderShouldReturn201() throws Exception {
        // Given
        OrderRequestDTO request = OrderRequestDTO.builder()
                .memberId(1L)
                .productId(2L)
                .quantity(1)
                .totalPrice(BigDecimal.TEN)
                .build();

        OrderResponseDTO response = OrderResponseDTO.builder()
                .id(UUID.randomUUID())
                .totalPrice(BigDecimal.TEN)
                .build();

        when(service.createOrder(any(OrderRequestDTO.class))).thenReturn(response);

        // When/Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());
    }
}
