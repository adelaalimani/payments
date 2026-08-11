package com.adela.payments.mapper;

import com.adela.payments.request.CreatePaymentRequest;
import com.adela.payments.response.PaymentResponse;
import com.adela.payments.entity.Payment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getMethod(),
                payment.getReference(),
                payment.getCreatedDate()
        );
    }

    public Payment toEntity(CreatePaymentRequest request) {
        return Payment.builder()
                .amount(request.amount())
                .method(request.method())
                .reference(UUID.randomUUID().toString())
                .build();
    }
}
