package com.adela.payments.service;

import com.adela.payments.entity.Payment;
import com.adela.payments.entity.User;
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
import com.adela.payments.utils.PageUtil;
import com.adela.payments.utils.SpecificationBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;

import static com.adela.payments.enums.PaymentStatus.REFUNDED;
import static com.adela.payments.enums.PaymentStatus.REFUND_REJECTED;
import static com.adela.payments.enums.RefundDecision.REFUND_ACCEPTED;

@Service
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final RedisTemplate<String, String> redisTemplate;
    Set<String> ALLOWED = Set.of(
            "id", "amount", "status", "method", "reference", "createdAt"
    );
    public PaymentService(PaymentRepository paymentRepository, PaymentMapper paymentMapper, RedisTemplate<String, String> redisTemplate) {
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentResponse initiatePayment(CreatePaymentRequest paymentRequest, String idempotencyKey) {

        User currentUser = (User) getAuthentication().getPrincipal();

        if (idempotencyKey != null) {
            return initiateWithIdempotencyKey(paymentRequest, idempotencyKey);
        }

        return initiateWithFallbackDedup(paymentRequest, currentUser);
    }

    private PaymentResponse initiateWithIdempotencyKey(CreatePaymentRequest paymentRequest, String idempotencyKey) {
        String redisKey = "idempotency:" + idempotencyKey;

        String existingValue = redisTemplate.opsForValue().get(redisKey);
        if (existingValue != null) {
            if ("PROCESSING".equals(existingValue)) {
                throw new ConflictException("This request is already being processed");
            }
            Optional<Payment> existingPayment = paymentRepository.findById(Long.parseLong(existingValue));
            if (existingPayment.isPresent()) {
                return paymentMapper.toResponse(existingPayment.get());
            }
            redisTemplate.delete(redisKey);
        }

        Boolean claimed = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "PROCESSING", Duration.ofMinutes(5));
        if (Boolean.FALSE.equals(claimed)) {
            throw new ConflictException("This request is already being processed");
        }

        Payment payment = createPayment(paymentRequest);
        redisTemplate.opsForValue().set(redisKey, payment.getId().toString(), Duration.ofMinutes(5));

        return paymentMapper.toResponse(payment);
    }

    private PaymentResponse initiateWithFallbackDedup(CreatePaymentRequest paymentRequest, User currentUser) {
        String rawKey = currentUser.getId() + "|" + paymentRequest.amount() + "|" + paymentRequest.method();
        String fallbackKey = "fallback:" + sha256Hex(rawKey);

        Boolean claimed = redisTemplate.opsForValue()
                .setIfAbsent(fallbackKey, "1", Duration.ofSeconds(60));
        if (Boolean.FALSE.equals(claimed)) {
            throw new BadRequestException("Duplicate request detected, please try again shortly");
        }

        return paymentMapper.toResponse(createPayment(paymentRequest));
    }

    private Payment createPayment(CreatePaymentRequest paymentRequest) {
        Payment payment = paymentMapper.toEntity(paymentRequest);
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);
        return payment;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Cacheable(value = "payments", key = "#id")
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id).orElseThrow(
                () -> new NotFoundException("Payment with id " + id + " not found"));
        checkAdminOrOwner(payment);

        return paymentMapper.toResponse(payment);
    }

    public Page<PaymentResponse> getAllPaymentsByFilter(CustomPageRequest pageRequest, String filter) {
        Pageable pageable = PageUtil.toPageable(pageRequest, ALLOWED);
        Specification<Payment> specification = buildSpecification(filter);
        Page<Payment> paymentResponse = paymentRepository.findAll(specification, pageable);
        return paymentResponse.map(paymentMapper::toResponse);
    }

    Specification<Payment> buildSpecification(String filter) {
        Set<String> ALLOWED = Set.of(
                "id", "amount", "status", "method", "reference", "createdAt"
        );
        Specification<Payment> specification = SpecificationBuilder.buildSpecification(filter, ALLOWED);
        if (isCurrentUserNotAdmin()) {
            Long currentUserId = ((User) getAuthentication().getPrincipal()).getId();
            Specification<Payment> ownerOnly = (root, query, cb) -> cb.equal(root.get("createdBy"), currentUserId);
            specification = specification.and(ownerOnly);
        }

        log.info("Specification: {}", specification);
        return specification;
    }

    public Resource exportPayments(String filter) {
        Specification<Payment> specification = buildSpecification(filter);
        List<Payment> payments = paymentRepository.findAll(specification);
        List<PaymentResponse> paymentResponses = payments.stream().map(paymentMapper::toResponse).toList();
        return exportAsJson(paymentResponses);
    }

    private Resource exportAsJson(List<PaymentResponse> paymentResponses) {
        StringBuilder csv = new StringBuilder();
        csv.append("id,amount,status,method,reference,createdDate\n");

        for (PaymentResponse p : paymentResponses) {
            csv.append(p.id()).append(",")
                    .append(p.amount()).append(",")
                    .append(p.status()).append(",")
                    .append(p.method()).append(",")
                    .append(p.reference()).append(",")
                    .append(p.createdDate()).append(",")
                    .append("\n");
        }
        return new ByteArrayResource(csv.toString().getBytes());
    }

    @CacheEvict(value = "payments", key = "#id")
    @Transactional
    public PaymentResponse requestRefund(Long id) {
        Payment payment = paymentRepository.findById(id).orElseThrow(
                () -> new NotFoundException("Payment with id " + id + " not found"));
        checkAdminOrOwner(payment);

        if (payment.getStatus() == REFUNDED) {
            throw new ConflictException("Payment already refunded");
        }

        if (payment.getStatus() == PaymentStatus.REFUND_REQUESTED) {
            throw new ConflictException("Refund already requested. Please wait for the refund to be processed.");
        }

        if (payment.getStatus() == REFUND_REJECTED) {
            throw new ConflictException("Refund already rejected. Please contact support for further assistance.");
        }

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new ConflictException("Cannot refund payment in status " + payment.getStatus() + "; only COMPLETED payments can be refunded");
        }
        payment.setStatus(PaymentStatus.REFUND_REQUESTED);
        paymentRepository.save(payment);
        return paymentMapper.toResponse(payment);
    }

    @Transactional
    @CacheEvict(value = "payments", key = "#id")
    public PaymentResponse cancelOrApproveRefund(Long id, RefundDecision refundResponse) {
        Payment payment = paymentRepository.findById(id).orElseThrow(
                () -> new NotFoundException("Payment with id " + id + " not found"));
        requireAdmin();

        if (payment.getStatus() != PaymentStatus.REFUND_REQUESTED) {
            throw new ConflictException(
                    "Cannot decide refund for payment in status " + payment.getStatus() +
                            "; a refund must be requested first");
        }

        PaymentStatus newStatus = refundResponse == REFUND_ACCEPTED ? REFUNDED : REFUND_REJECTED;
        payment.setStatus(newStatus);
        paymentRepository.save(payment);
        return paymentMapper.toResponse(payment);
    }

    private Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private void checkAdminOrOwner(Payment payment) {
        if (isCurrentUserNotAdmin()) {
            User currentUser = (User) getAuthentication().getPrincipal();
            if (!payment.getCreatedBy().equals(Objects.requireNonNull(currentUser).getId())) {
                throw new ForbiddenException("You are not authorized to perform this action on this payment");
            }
        }
    }

    private void requireAdmin() {
        if (isCurrentUserNotAdmin()) {
            throw new ForbiddenException("Only administrators can perform this action");
        }
    }

    private boolean isCurrentUserNotAdmin() {
        return Objects.requireNonNull(getAuthentication()).getAuthorities().stream()
                .noneMatch(auth -> Objects.equals(auth.getAuthority(), "ROLE_ADMIN"));
    }


}
