package com.sotatek.order.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.sotatek.order.dto.OrderRequestDTO;
import com.sotatek.order.entity.OrderStatus;
import com.sotatek.order.exception.InvalidMemberException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    private WireMockServer wireMockServer;

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(8081);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
    }

    @AfterEach
    void teardown() {
        wireMockServer.stop();
    }

    @Test
    void createOrder_shouldFail_whenMemberInactive() {
        Long memberId = 2L;

        stubFor(get(urlEqualTo("/members/" + memberId))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":" + memberId + ",\"exists\":true,\"active\":false}")));

        OrderRequestDTO request = new OrderRequestDTO();
        request.setMemberId(memberId);
        request.setProductId(UUID.randomUUID());
        request.setQuantity(1);
        request.setTotalPrice(BigDecimal.valueOf(100));
        request.setStatus(OrderStatus.PENDING);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InvalidMemberException.class);
    }

    @Test
    void createOrder_shouldFail_whenPaymentFails() {
        Long memberId = 1L;
        Long productId = 101L;
        BigDecimal price = BigDecimal.valueOf(2000); // High price to trigger mock failure

        // Stub Member OK
        stubFor(get(urlEqualTo("/members/" + memberId))
                .willReturn(okJson("{\"id\":" + memberId + ",\"exists\":true,\"active\":true}")));

        // Stub Product OK
        stubFor(get(urlPathMatching("/products/" + productId + "/stock"))
                .withQueryParam("quantity", equalTo("1"))
                .willReturn(
                        okJson("{\"id\":" + productId + ",\"available\":true,\"stock\":10,\"price\":" + price + "}")));

        // Stub Payment Failure
        stubFor(post(urlEqualTo("/payments/process"))
                .willReturn(aResponse()
                        .withStatus(400) // Bad Request or other error status
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Payment failed due to insufficient funds\"}")));

        OrderRequestDTO request = new OrderRequestDTO();
        request.setMemberId(memberId);
        request.setProductId(productId); // Use Long productId
        request.setQuantity(1);
        request.setTotalPrice(price);
        request.setStatus(OrderStatus.PENDING);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(com.sotatek.order.exception.PaymentFailedException.class);
    }

    @Test
    void createOrder_shouldFail_whenOutOfStock() {
        Long memberId = 1L;
        Long productId = 103L;

        // Stub Member OK
        stubFor(get(urlEqualTo("/members/" + memberId))
                .willReturn(okJson("{\"id\":" + memberId + ",\"exists\":true,\"active\":true}")));

        // Stub Product Out of Stock
        stubFor(get(urlPathMatching("/products/" + productId + "/stock"))
                .withQueryParam("quantity", equalTo("1"))
                .willReturn(okJson("{\"id\":" + productId + ",\"available\":true,\"stock\":0,\"price\":25.00}")));

        OrderRequestDTO request = new OrderRequestDTO();
        request.setMemberId(memberId);
        request.setProductId(productId);
        request.setQuantity(1);
        request.setTotalPrice(BigDecimal.valueOf(25));
        request.setStatus(OrderStatus.PENDING); // Added missing status

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(com.sotatek.order.exception.OutOfStockException.class);
    }
}
