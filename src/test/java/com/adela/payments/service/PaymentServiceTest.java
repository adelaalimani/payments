package com.adela.payments.service;

import com.adela.payments.config.AnalyticsResponse;
import com.adela.payments.entity.Payment;
import com.adela.payments.entity.Role;
import com.adela.payments.entity.User;
import com.adela.payments.enums.PaymentMethod;
import com.adela.payments.enums.PaymentStatus;
import com.adela.payments.enums.RefundDecision;
import com.adela.payments.exception.BadRequestException;
import com.adela.payments.exception.ConflictException;
import com.adela.payments.exception.ForbiddenException;
import com.adela.payments.exception.NotFoundException;
import com.adela.payments.mapper.PaymentMapper;
import com.adela.payments.repository.PaymentRepository;
import com.adela.payments.request.CreatePaymentRequest;
import com.adela.payments.request.CustomPageRequest;
import com.adela.payments.response.PaymentResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        paymentService = new PaymentService(paymentRepository, paymentMapper, redisTemplate);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User customer(Long id) {
        return User.builder()
                .id(id)
                .firstName("Adela")
                .lastName("Alimani")
                .email("customer" + id + "@gmail.com")
                .phoneNumber("+1000000000" + id)
                .password("hashed")
                .enabled(true)
                .roles(List.of(Role.builder().name("CUSTOMER").build()))
                .build();
    }

    private User admin(Long id) {
        return User.builder()
                .id(id)
                .firstName("Admin")
                .lastName("Admin")
                .email("admin" + id + "@gmail.com")
                .phoneNumber("+2000000000" + id)
                .password("hashed")
                .enabled(true)
                .roles(List.of(Role.builder().name("ADMIN").build()))
                .build();
    }

    private void authenticateAs(User user) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Payment paymentOf(Long id, Long createdBy, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setCreatedBy(createdBy);
        payment.setStatus(status);
        payment.setAmount(BigDecimal.TEN);
        payment.setMethod(PaymentMethod.CREDIT_CARD);
        payment.setReference("ref-" + id);
        payment.setCreatedDate(LocalDateTime.now());
        return payment;
    }

    private PaymentResponse responseOf(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getAmount(), payment.getStatus(),
                payment.getMethod(), payment.getReference(), payment.getCreatedDate());
    }

    // ---------- initiatePayment (idempotency key path) ----------

    @Test
    void initiatePayment_withIdempotencyKey_noExistingValue_claimsAndCreatesPayment() {
        User user = customer(1L);
        authenticateAs(user);
        CreatePaymentRequest request = new CreatePaymentRequest(BigDecimal.TEN, PaymentMethod.CREDIT_CARD);
        Payment created = paymentOf(5L, 1L, PaymentStatus.PENDING);
        PaymentResponse response = responseOf(created);

        when(valueOperations.get("idempotency:key-1")).thenReturn(null);
        when(valueOperations.setIfAbsent(eq("idempotency:key-1"), eq("PROCESSING"), any(Duration.class))).thenReturn(true);
        when(paymentMapper.toEntity(request)).thenReturn(created);
        when(paymentMapper.toResponse(created)).thenReturn(response);

        PaymentResponse result = paymentService.initiatePayment(request, "key-1");

        assertThat(result).isEqualTo(response);
        verify(paymentRepository).save(created);
        verify(valueOperations).set("idempotency:key-1", "5", Duration.ofMinutes(5));
        assertThat(created.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void initiatePayment_withIdempotencyKey_processing_throwsConflict() {
        authenticateAs(customer(1L));
        CreatePaymentRequest request = new CreatePaymentRequest(BigDecimal.TEN, PaymentMethod.CREDIT_CARD);
        when(valueOperations.get("idempotency:key-2")).thenReturn("PROCESSING");

        assertThatThrownBy(() -> paymentService.initiatePayment(request, "key-2"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already being processed");
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void initiatePayment_withIdempotencyKey_existingCompletedPayment_returnsCachedResponse() {
        authenticateAs(customer(1L));
        CreatePaymentRequest request = new CreatePaymentRequest(BigDecimal.TEN, PaymentMethod.CREDIT_CARD);
        Payment existing = paymentOf(7L, 1L, PaymentStatus.PENDING);
        PaymentResponse response = responseOf(existing);

        when(valueOperations.get("idempotency:key-3")).thenReturn("7");
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(paymentMapper.toResponse(existing)).thenReturn(response);

        PaymentResponse result = paymentService.initiatePayment(request, "key-3");

        assertThat(result).isEqualTo(response);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void initiatePayment_withIdempotencyKey_existingPaymentDeleted_deletesKeyThenClaimsNew() {
        authenticateAs(customer(1L));
        CreatePaymentRequest request = new CreatePaymentRequest(BigDecimal.TEN, PaymentMethod.CREDIT_CARD);
        Payment created = paymentOf(9L, 1L, PaymentStatus.PENDING);
        PaymentResponse response = responseOf(created);

        when(valueOperations.get("idempotency:key-4")).thenReturn("8");
        when(paymentRepository.findById(8L)).thenReturn(Optional.empty());
        when(valueOperations.setIfAbsent(eq("idempotency:key-4"), eq("PROCESSING"), any(Duration.class))).thenReturn(true);
        when(paymentMapper.toEntity(request)).thenReturn(created);
        when(paymentMapper.toResponse(created)).thenReturn(response);

        PaymentResponse result = paymentService.initiatePayment(request, "key-4");

        assertThat(result).isEqualTo(response);
        verify(redisTemplate).delete("idempotency:key-4");
        verify(paymentRepository).save(created);
    }

    @Test
    void initiatePayment_withIdempotencyKey_claimRaceLost_throwsConflict() {
        authenticateAs(customer(1L));
        CreatePaymentRequest request = new CreatePaymentRequest(BigDecimal.TEN, PaymentMethod.CREDIT_CARD);

        when(valueOperations.get("idempotency:key-5")).thenReturn(null);
        when(valueOperations.setIfAbsent(eq("idempotency:key-5"), eq("PROCESSING"), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> paymentService.initiatePayment(request, "key-5"))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(paymentRepository);
    }

    // ---------- initiatePayment (fallback dedup path, no idempotency key) ----------

    @Test
    void initiatePayment_withoutIdempotencyKey_claimSucceeds_createsPayment() {
        User user = customer(2L);
        authenticateAs(user);
        CreatePaymentRequest request = new CreatePaymentRequest(BigDecimal.TEN, PaymentMethod.BANK_TRANSFER);
        Payment created = paymentOf(11L, 2L, PaymentStatus.PENDING);
        PaymentResponse response = responseOf(created);

        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60)))).thenReturn(true);
        when(paymentMapper.toEntity(request)).thenReturn(created);
        when(paymentMapper.toResponse(created)).thenReturn(response);

        PaymentResponse result = paymentService.initiatePayment(request, null);

        assertThat(result).isEqualTo(response);
        verify(paymentRepository).save(created);
    }

    @Test
    void initiatePayment_withoutIdempotencyKey_duplicateDetected_throwsBadRequest() {
        authenticateAs(customer(2L));
        CreatePaymentRequest request = new CreatePaymentRequest(BigDecimal.TEN, PaymentMethod.BANK_TRANSFER);

        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60)))).thenReturn(false);

        assertThatThrownBy(() -> paymentService.initiatePayment(request, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Duplicate request");
        verifyNoInteractions(paymentRepository);
    }

    // ---------- getPaymentById ----------

    @Test
    void getPaymentById_ownerCanAccess() {
        User user = customer(3L);
        authenticateAs(user);
        Payment payment = paymentOf(20L, 3L, PaymentStatus.COMPLETED);
        PaymentResponse response = responseOf(payment);

        when(paymentRepository.findById(20L)).thenReturn(Optional.of(payment));
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        assertThat(paymentService.getPaymentById(20L)).isEqualTo(response);
    }

    @Test
    void getPaymentById_notFound_throwsNotFoundException() {
        authenticateAs(customer(3L));
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentById(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getPaymentById_nonOwnerNonAdmin_throwsForbidden() {
        authenticateAs(customer(3L));
        Payment payment = paymentOf(21L, 999L, PaymentStatus.COMPLETED);
        when(paymentRepository.findById(21L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.getPaymentById(21L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getPaymentById_admin_canAccessAnyPayment() {
        authenticateAs(admin(4L));
        Payment payment = paymentOf(22L, 999L, PaymentStatus.COMPLETED);
        PaymentResponse response = responseOf(payment);
        when(paymentRepository.findById(22L)).thenReturn(Optional.of(payment));
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        assertThat(paymentService.getPaymentById(22L)).isEqualTo(response);
    }

    // ---------- getAllPaymentsByFilter ----------

    @Test
    void getAllPaymentsByFilter_admin_returnsMappedPage() {
        authenticateAs(admin(5L));
        Payment payment = paymentOf(30L, 1L, PaymentStatus.COMPLETED);
        PaymentResponse response = responseOf(payment);
        Page<Payment> page = new PageImpl<>(List.of(payment));

        when(paymentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        Page<PaymentResponse> result = paymentService.getAllPaymentsByFilter(pageRequest("id"), null);

        assertThat(result.getContent()).containsExactly(response);
    }

    @Test
    void getAllPaymentsByFilter_nonAdmin_appliesOwnerFilterAndReturnsMappedPage() {
        authenticateAs(customer(6L));
        Payment payment = paymentOf(31L, 6L, PaymentStatus.COMPLETED);
        PaymentResponse response = responseOf(payment);
        Page<Payment> page = new PageImpl<>(List.of(payment));

        when(paymentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        Page<PaymentResponse> result = paymentService.getAllPaymentsByFilter(pageRequest("id"), null);

        assertThat(result.getContent()).containsExactly(response);
    }

    @Test
    void getAllPaymentsByFilter_unsupportedSortField_throwsBadRequest() {
        authenticateAs(admin(5L));
        assertThatThrownBy(() -> paymentService.getAllPaymentsByFilter(pageRequest("unknownField"), null))
                .isInstanceOf(BadRequestException.class);
    }

    private CustomPageRequest pageRequest(String sortBy) {
        CustomPageRequest request = new CustomPageRequest();
        request.setSortBy(sortBy);
        return request;
    }

    // ---------- exportPayments ----------

    @Test
    void exportPayments_buildsCsvResource() throws IOException {
        authenticateAs(admin(5L));
        Payment payment = paymentOf(40L, 1L, PaymentStatus.COMPLETED);
        PaymentResponse response = responseOf(payment);

        when(paymentRepository.findAll(any(Specification.class))).thenReturn(List.of(payment));
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        Resource resource = paymentService.exportPayments(null);

        String csv = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("id,amount,status,method,reference,createdDate");
        assertThat(csv).contains(String.valueOf(payment.getId()));
    }

    // ---------- requestRefund ----------

    @Test
    void requestRefund_completedPayment_transitionsToRefundRequested() {
        authenticateAs(customer(7L));
        Payment payment = paymentOf(50L, 7L, PaymentStatus.COMPLETED);
        PaymentResponse response = responseOf(payment);
        when(paymentRepository.findById(50L)).thenReturn(Optional.of(payment));
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        paymentService.requestRefund(50L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_REQUESTED);
        verify(paymentRepository).save(payment);
    }

    @Test
    void requestRefund_notFound_throwsNotFoundException() {
        authenticateAs(customer(7L));
        when(paymentRepository.findById(51L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> paymentService.requestRefund(51L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void requestRefund_nonOwner_throwsForbidden() {
        authenticateAs(customer(7L));
        Payment payment = paymentOf(52L, 999L, PaymentStatus.COMPLETED);
        when(paymentRepository.findById(52L)).thenReturn(Optional.of(payment));
        assertThatThrownBy(() -> paymentService.requestRefund(52L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requestRefund_alreadyRefunded_throwsConflict() {
        authenticateAs(customer(7L));
        Payment payment = paymentOf(53L, 7L, PaymentStatus.REFUNDED);
        when(paymentRepository.findById(53L)).thenReturn(Optional.of(payment));
        assertThatThrownBy(() -> paymentService.requestRefund(53L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already refunded");
    }

    @Test
    void requestRefund_alreadyRequested_throwsConflict() {
        authenticateAs(customer(7L));
        Payment payment = paymentOf(54L, 7L, PaymentStatus.REFUND_REQUESTED);
        when(paymentRepository.findById(54L)).thenReturn(Optional.of(payment));
        assertThatThrownBy(() -> paymentService.requestRefund(54L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already requested");
    }

    @Test
    void requestRefund_alreadyRejected_throwsConflict() {
        authenticateAs(customer(7L));
        Payment payment = paymentOf(55L, 7L, PaymentStatus.REFUND_REJECTED);
        when(paymentRepository.findById(55L)).thenReturn(Optional.of(payment));
        assertThatThrownBy(() -> paymentService.requestRefund(55L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already rejected");
    }

    @Test
    void requestRefund_notCompleted_throwsConflict() {
        authenticateAs(customer(7L));
        Payment payment = paymentOf(56L, 7L, PaymentStatus.PENDING);
        when(paymentRepository.findById(56L)).thenReturn(Optional.of(payment));
        assertThatThrownBy(() -> paymentService.requestRefund(56L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("only COMPLETED payments");
    }

    // ---------- cancelOrApproveRefund ----------

    @Test
    void cancelOrApproveRefund_accepted_transitionsToRefunded() {
        authenticateAs(admin(8L));
        Payment payment = paymentOf(60L, 7L, PaymentStatus.REFUND_REQUESTED);
        PaymentResponse response = responseOf(payment);
        when(paymentRepository.findById(60L)).thenReturn(Optional.of(payment));
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        paymentService.cancelOrApproveRefund(60L, RefundDecision.REFUND_ACCEPTED);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void cancelOrApproveRefund_rejected_transitionsToRefundRejected() {
        authenticateAs(admin(8L));
        Payment payment = paymentOf(61L, 7L, PaymentStatus.REFUND_REQUESTED);
        PaymentResponse response = responseOf(payment);
        when(paymentRepository.findById(61L)).thenReturn(Optional.of(payment));
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        paymentService.cancelOrApproveRefund(61L, RefundDecision.REFUND_REJECTED);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_REJECTED);
    }

    @Test
    void cancelOrApproveRefund_notFound_throwsNotFoundException() {
        authenticateAs(admin(8L));
        when(paymentRepository.findById(62L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> paymentService.cancelOrApproveRefund(62L, RefundDecision.REFUND_ACCEPTED))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancelOrApproveRefund_nonAdmin_throwsForbidden() {
        authenticateAs(customer(9L));
        Payment payment = paymentOf(63L, 9L, PaymentStatus.REFUND_REQUESTED);
        when(paymentRepository.findById(63L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.cancelOrApproveRefund(63L, RefundDecision.REFUND_ACCEPTED))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cancelOrApproveRefund_notRequested_throwsConflict() {
        authenticateAs(admin(8L));
        Payment payment = paymentOf(64L, 7L, PaymentStatus.PENDING);
        when(paymentRepository.findById(64L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.cancelOrApproveRefund(64L, RefundDecision.REFUND_ACCEPTED))
                .isInstanceOf(ConflictException.class);
    }

    // ---------- getAnalytics ----------

    @Test
    void getAnalytics_admin_aggregatesRows() {
        authenticateAs(admin(10L));
        Object[] row1 = {PaymentMethod.CREDIT_CARD, PaymentStatus.COMPLETED, 3L, BigDecimal.valueOf(300)};
        Object[] row2 = {PaymentMethod.BANK_TRANSFER, PaymentStatus.FAILED, 1L, BigDecimal.valueOf(50)};
        when(paymentRepository.getAnalyticsRaw(null, null)).thenReturn(List.of(row1, row2));

        AnalyticsResponse response = paymentService.getAnalytics(null, null);

        assertThat(response.totalTransactions()).isEqualTo(4);
        assertThat(response.totalVolume()).isEqualByComparingTo(BigDecimal.valueOf(350));
        assertThat(response.successRate()).isEqualTo(75.0);
        assertThat(response.breakdownByMethod()).containsKeys("CREDIT_CARD", "BANK_TRANSFER");
        assertThat(response.breakdownByMethod().get("CREDIT_CARD").count()).isEqualTo(3L);
    }

    @Test
    void getAnalytics_noRows_returnsZeroSuccessRate() {
        authenticateAs(admin(10L));
        when(paymentRepository.getAnalyticsRaw(null, null)).thenReturn(List.of());

        AnalyticsResponse response = paymentService.getAnalytics(null, null);

        assertThat(response.totalTransactions()).isZero();
        assertThat(response.successRate()).isZero();
        assertThat(response.breakdownByMethod()).isEmpty();
    }

    @Test
    void getAnalytics_nonAdmin_throwsForbidden() {
        authenticateAs(customer(11L));
        assertThatThrownBy(() -> paymentService.getAnalytics(null, null))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(paymentRepository);
    }
}