package com.adela.payments.service;

import com.adela.payments.request.CreatePaymentRequest;
import com.adela.payments.response.PaymentResponse;
import com.adela.payments.entity.Payment;
import com.adela.payments.enums.PaymentMethod;
import com.adela.payments.enums.PaymentStatus;
import com.adela.payments.exception.BadRequestException;
import com.adela.payments.exception.NotFoundException;
import com.adela.payments.mapper.PaymentMapper;
import com.adela.payments.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentMapper paymentMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;


    @Nested
    class InitiatePayment {

        private PaymentService paymentService;

        @BeforeEach
        void setUp() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            paymentService = new PaymentService(paymentRepository, paymentMapper, redisTemplate);
        }

        @Test
        void newPaymentWithIdempotencyKey_savesAndReturnsResponse() {
            CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("100.00"), PaymentMethod.CREDIT_CARD);
            Payment payment = Payment.builder().id(1L).amount(request.amount()).method(request.method()).build();
            PaymentResponse expected = new PaymentResponse(1L, request.amount(), PaymentStatus.PENDING, request.method(), "ref-1");

            when(valueOperations.get("idempotency:key-abc")).thenReturn(null);
            when(paymentMapper.toEntity(request)).thenReturn(payment);
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(paymentMapper.toResponse(payment)).thenReturn(expected);

            PaymentResponse result = paymentService.initiatePayment(request, "key-abc");

            assertThat(result).isEqualTo(expected);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            verify(paymentRepository).save(payment);
            verify(valueOperations).set("idempotency:key-abc", "1", Duration.ofMinutes(5));
        }

        @Test
        void existingIdempotencyKey_returnsCachedPaymentWithoutSaving() {
            CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("100.00"), PaymentMethod.CREDIT_CARD);
            Payment existing = Payment.builder().id(42L).build();
            PaymentResponse expected = new PaymentResponse(42L, request.amount(), PaymentStatus.PENDING, request.method(), "ref-42");

            when(valueOperations.get("idempotency:key-abc")).thenReturn("42");
            when(paymentRepository.findById(42L)).thenReturn(Optional.of(existing));
            when(paymentMapper.toResponse(existing)).thenReturn(expected);

            PaymentResponse result = paymentService.initiatePayment(request, "key-abc");

            assertThat(result).isEqualTo(expected);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void staleIdempotencyKey_deletesKeyAndCreatesNewPayment() {
            CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("100.00"), PaymentMethod.CREDIT_CARD);
            Payment newPayment = Payment.builder().id(99L).amount(request.amount()).method(request.method()).build();
            PaymentResponse expected = new PaymentResponse(99L, request.amount(), PaymentStatus.PENDING, request.method(), "ref-99");

            when(valueOperations.get("idempotency:key-stale")).thenReturn("999");
            when(paymentRepository.findById(999L)).thenReturn(Optional.empty());
            when(paymentMapper.toEntity(request)).thenReturn(newPayment);
            when(paymentRepository.save(newPayment)).thenReturn(newPayment);
            when(paymentMapper.toResponse(newPayment)).thenReturn(expected);

            PaymentResponse result = paymentService.initiatePayment(request, "key-stale");

            assertThat(result).isEqualTo(expected);
            verify(redisTemplate).delete("idempotency:key-stale");
            verify(paymentRepository).save(newPayment);
        }

        @Test
        void nullIdempotencyKey_usesFallbackKey() {
            CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("50.00"), PaymentMethod.BANK_TRANSFER);
            Payment payment = Payment.builder().id(7L).amount(request.amount()).method(request.method()).build();
            PaymentResponse expected = new PaymentResponse(7L, request.amount(), PaymentStatus.PENDING, request.method(), "ref-7");

            when(valueOperations.get("idempotency:null")).thenReturn(null);
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
            when(paymentMapper.toEntity(request)).thenReturn(payment);
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(paymentMapper.toResponse(payment)).thenReturn(expected);

            PaymentResponse result = paymentService.initiatePayment(request, null);

            assertThat(result).isEqualTo(expected);
            verify(valueOperations).setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(10)));
        }

        @Test
        void nullIdempotencyKey_fallbackKeyAlreadyExists_throwsBadRequestException() {
            CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("50.00"), PaymentMethod.BANK_TRANSFER);

            when(valueOperations.get("idempotency:null")).thenReturn(null);
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

            assertThatThrownBy(() -> paymentService.initiatePayment(request, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid idempotency key");

            verify(paymentRepository, never()).save(any());
        }
    }


    @Nested
    class GetPaymentById {

        private PaymentService paymentService;

        @BeforeEach
        void setUp() {
            // No Redis needed here — clean setup with no unnecessary stubs
            paymentService = new PaymentService(paymentRepository, paymentMapper, redisTemplate);
        }

        @Test
        void found_returnsResponse() {
            Payment payment = Payment.builder().id(1L).build();
            PaymentResponse expected = new PaymentResponse(1L, BigDecimal.TEN, PaymentStatus.PENDING, PaymentMethod.CREDIT_CARD, "ref-1");

            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(paymentMapper.toResponse(payment)).thenReturn(expected);

            assertThat(paymentService.getPaymentById(1L)).isEqualTo(expected);
        }

        @Test
        void notFound_throwsNotFoundException() {
            when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPaymentById(99L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Payment with id 99 not found");
        }
    }
}