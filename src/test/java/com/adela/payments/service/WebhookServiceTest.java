package com.adela.payments.service;

import com.adela.payments.entity.Payment;
import com.adela.payments.enums.PaymentStatus;
import com.adela.payments.exception.BadRequestException;
import com.adela.payments.exception.NotFoundException;
import com.adela.payments.exception.UnAuthorizedException;
import com.adela.payments.repository.PaymentRepository;
import com.adela.payments.utils.SignatureUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    private static final String SECRET = "test-secret";

    @Mock
    private PaymentRepository paymentRepository;

    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(paymentRepository, new ObjectMapper());
        ReflectionTestUtils.setField(webhookService, "webhookSecret", SECRET);
    }

    private String sign(String payload) {
        return computeSignature(payload, SECRET);
    }

    private String computeSignature(String payload, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Payment paymentWithStatus(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setReference("ref-123");
        payment.setStatus(status);
        return payment;
    }

    @Test
    void processWebhook_invalidSignature_throwsUnAuthorized() {
        String payload = "{\"reference\":\"ref-123\",\"status\":\"COMPLETED\"}";

        assertThatThrownBy(() -> webhookService.processWebhook(payload, "not-a-valid-signature"))
                .isInstanceOf(UnAuthorizedException.class);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void processWebhook_malformedJsonPayload_throwsBadRequest() {
        String payload = "not-json";
        String signature = sign(payload);

        assertThatThrownBy(() -> webhookService.processWebhook(payload, signature))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Failed to parse webhook payload");
    }

    @Test
    void processWebhook_paymentNotFound_throwsBadRequest() {
        String payload = "{\"reference\":\"missing-ref\",\"status\":\"COMPLETED\"}";
        String signature = sign(payload);
        when(paymentRepository.findByReference("missing-ref")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> webhookService.processWebhook(payload, signature))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Payment not found");
    }

    @Test
    void processWebhook_unrecognizedStatus_throwsNotFoundException() {
        String payload = "{\"reference\":\"ref-123\",\"status\":\"WEIRD\"}";
        String signature = sign(payload);
        when(paymentRepository.findByReference("ref-123")).thenReturn(Optional.of(paymentWithStatus(PaymentStatus.PENDING)));

        assertThatThrownBy(() -> webhookService.processWebhook(payload, signature))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Unrecognized webhook status");
    }

    @Test
    void processWebhook_sameStatusAsCurrent_isNoOp() {
        String payload = "{\"reference\":\"ref-123\",\"status\":\"COMPLETED\"}";
        String signature = sign(payload);
        Payment payment = paymentWithStatus(PaymentStatus.COMPLETED);
        when(paymentRepository.findByReference("ref-123")).thenReturn(Optional.of(payment));

        webhookService.processWebhook(payload, signature);

        verify(paymentRepository, never()).save(any());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void processWebhook_pendingToCompleted_updatesStatusAndSaves() {
        String payload = "{\"reference\":\"ref-123\",\"status\":\"SUCCEEDED\"}";
        String signature = sign(payload);
        Payment payment = paymentWithStatus(PaymentStatus.PENDING);
        when(paymentRepository.findByReference("ref-123")).thenReturn(Optional.of(payment));

        webhookService.processWebhook(payload, signature);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepository).save(payment);
    }

    @Test
    void processWebhook_pendingToFailed_updatesStatusAndSaves() {
        String payload = "{\"reference\":\"ref-123\",\"status\":\"DECLINED\"}";
        String signature = sign(payload);
        Payment payment = paymentWithStatus(PaymentStatus.PENDING);
        when(paymentRepository.findByReference("ref-123")).thenReturn(Optional.of(payment));

        webhookService.processWebhook(payload, signature);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
    }

    @Test
    void processWebhook_nonPendingDifferentStatus_throwsIllegalState() {
        String payload = "{\"reference\":\"ref-123\",\"status\":\"COMPLETED\"}";
        String signature = sign(payload);
        Payment payment = paymentWithStatus(PaymentStatus.FAILED);
        when(paymentRepository.findByReference("ref-123")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> webhookService.processWebhook(payload, signature))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot update payment from status");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void signatureUtil_isConsistentWithComputedSignature() {
        String payload = "{\"reference\":\"ref-123\",\"status\":\"COMPLETED\"}";
        String signature = sign(payload);

        assertThat(SignatureUtil.isValidSignature(payload, signature, SECRET)).isTrue();
        assertThat(SignatureUtil.isValidSignature(payload, signature, "wrong-secret")).isFalse();
    }
}