package com.sotatek.order.infrastructure.client;

import com.sotatek.order.infrastructure.client.dto.MemberResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "member-service", url = "${member.service.url}", primary = false)
public interface MemberClient {

    @GetMapping("/members/{id}")
    @CircuitBreaker(name = "memberService", fallbackMethod = "getMemberFallback")
    @Retry(name = "memberService")
    MemberResponse getMember(@PathVariable("id") Long id);

    default MemberResponse getMemberFallback(Long id, Throwable throwable) {
        return new MemberResponse(id, false, false);
    }
}
