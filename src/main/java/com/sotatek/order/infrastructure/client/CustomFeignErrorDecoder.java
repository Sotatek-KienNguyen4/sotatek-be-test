package com.sotatek.order.infrastructure.client;

import com.sotatek.order.exception.ExternalServiceClientException;
import com.sotatek.order.exception.ServiceUnavailableException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CustomFeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        HttpStatus status = HttpStatus.resolve(response.status());

        if (status == HttpStatus.SERVICE_UNAVAILABLE || status == HttpStatus.GATEWAY_TIMEOUT) {
            return new ServiceUnavailableException("External service unavailable: " + methodKey);
        }

        if (status != null && status.is4xxClientError()) {
            return new ExternalServiceClientException("Client error from " + methodKey + ": " + response.status());
        }

        if (status != null && status.is5xxServerError()) {
            return new ServiceUnavailableException("Server error from " + methodKey + ": " + response.status());
        }

        return defaultDecoder.decode(methodKey, response);
    }
}
