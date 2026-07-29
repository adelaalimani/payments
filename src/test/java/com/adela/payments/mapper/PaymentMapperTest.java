package com.adela.payments.mapper;

import com.adela.payments.request.CreatePaymentRequest;
import com.adela.payments.response.PaymentResponse;
import com.adela.payments.entity.Payment;
import com.adela.payments.enums.PaymentMethod;
import com.adela.payments.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMapperTest {

    private final PaymentMapper mapper = new PaymentMapper();

    @Test
    void toEntity_mapsFieldsCorrectly() {
        CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("100.00"), PaymentMethod.CREDIT_CARD);

        Payment payment = mapper.toEntity(request);

        assertThat(payment.getAmount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(payment.getReference()).isNotNull().isNotEmpty();
        assertThat(payment.getCreatedAt()).isNotNull().isBefore(LocalDateTime.now().plusSeconds(1));
        assertThat(payment.getStatus()).isNull();
    }

    @Test
    void toEntity_generatesUniqueReferences() {
        CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("50.00"), PaymentMethod.BANK_TRANSFER);

        Payment p1 = mapper.toEntity(request);
        Payment p2 = mapper.toEntity(request);

        assertThat(p1.getReference()).isNotEqualTo(p2.getReference());
    }

    @Test
    void toResponse_mapsFieldsCorrectly() {
        Payment payment = Payment.builder()
                .id(1L)
                .amount(new BigDecimal("200.00"))
                .status(PaymentStatus.COMPLETED)
                .method(PaymentMethod.BANK_TRANSFER)
                .reference("ref-abc")
                .build();

        PaymentResponse response = mapper.toResponse(payment);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.amount()).isEqualTo(new BigDecimal("200.00"));
        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.method()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(response.reference()).isEqualTo("ref-abc");
    }
}