package com.sotatek.order.infrastructure.client;

import com.sotatek.order.infrastructure.client.dto.MemberResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "dev")
public class MockMemberClient implements MemberClient {

    @Override
    public MemberResponse getMember(Long id) {
        // Mock specific IDs for testing
        if (id.equals(1L)) {
            return new MemberResponse(id, true, true); // Active
        } else if (id.equals(2L)) {
            return new MemberResponse(id, true, false); // Inactive
        }
        return new MemberResponse(id, false, false); // Not exists
    }
}
