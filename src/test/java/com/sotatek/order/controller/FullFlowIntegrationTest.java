package com.sotatek.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.sotatek.order.dto.OrderRequestDTO;
import com.sotatek.order.entity.OrderStatus;
import com.sotatek.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FullFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private WireMockServer wireMockServer;

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(8081);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
        orderRepository.deleteAll();
    }

    @AfterEach
    void teardown() {
        wireMockServer.stop();
    }

    @Test
    void createOrder_happyPath_shouldCreateAndConfirmOrder() throws Exception {
        Long memberId = 1L;
        Long productId = 101L;
        BigDecimal price = BigDecimal.valueOf(100);
        int quantity = 2;
        BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(quantity)); // 200

        // 1. Stub Member Service (OK)
        stubFor(get(urlEqualTo("/members/" + memberId))
                .willReturn(okJson("{\"id\":" + memberId + ",\"exists\":true,\"active\":true}")));

        // 2. Stub Product Service (Available, Enough Stock)
        stubFor(get(urlPathMatching("/products/" + productId + "/stock"))
                .withQueryParam("quantity", equalTo(String.valueOf(quantity)))
                .willReturn(okJson(
                        "{\"id\":\"" + productId + "\",\"available\":true,\"stock\":10,\"price\":" + price + "}")));

        // 3. Stub Payment Service (Success)
        stubFor(post(urlEqualTo("/payments"))
                .willReturn(okJson("{\"transactionId\":\"tx-123\",\"success\":true,\"message\":\"Success\"}")));

        OrderRequestDTO request = OrderRequestDTO.builder()
                .memberId(memberId)
                .productId(productId) // Uses UUID now
                .quantity(quantity)
                .totalPrice(totalPrice)
                .build();

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderRepository.findAll().get(0).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void createOrder_paymentFails_shouldRollbackOrder() throws Exception {
        Long memberId = 1L;
        Long productId = 101L;
        BigDecimal price = BigDecimal.valueOf(100);
        int quantity = 1;
        BigDecimal totalPrice = price;

        // 1. Stub Member Service (OK)
        stubFor(get(urlEqualTo("/members/" + memberId))
                .willReturn(okJson("{\"id\":" + memberId + ",\"exists\":true,\"active\":true}")));

        // 2. Stub Product Service (OK)
        stubFor(get(urlPathMatching("/products/" + productId + "/stock"))
                .withQueryParam("quantity", equalTo("1"))
                .willReturn(okJson(
                        "{\"id\":\"" + productId + "\",\"available\":true,\"stock\":10,\"price\":" + price + "}")));

        // 3. Stub Payment Service (Fail)
        stubFor(post(urlEqualTo("/payments"))
                .willReturn(okJson("{\"transactionId\":null,\"success\":false,\"message\":\"Insufficient funds\"}")));

        OrderRequestDTO request = OrderRequestDTO.builder()
                .memberId(memberId)
                .productId(productId)
                .quantity(quantity)
                .totalPrice(totalPrice)
                .build();

        // Expect 422 Unprocessable Entity
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());

        // Verify Rollback: Order should NOT be in the database
        assertThat(orderRepository.count()).isEqualTo(0);
    }
}
