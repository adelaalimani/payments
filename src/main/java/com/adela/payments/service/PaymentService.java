package com.adela.payments.service;

import com.adela.payments.entity.Payment;
import com.adela.payments.enums.PaymentStatus;
import com.adela.payments.enums.RefundDecision;
import com.adela.payments.exception.BadRequestException;
import com.adela.payments.exception.ConflictException;
import com.adela.payments.exception.NotFoundException;
import com.adela.payments.mapper.PaymentMapper;
import com.adela.payments.repository.PaymentRepository;
import com.adela.payments.request.CreatePaymentRequest;
import com.adela.payments.request.CustomPageRequest;
import com.adela.payments.response.PaymentResponse;
import com.adela.payments.utils.PageUtil;
import com.adela.payments.utils.SpecificationBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static com.adela.payments.enums.PaymentStatus.REFUNDED;
import static com.adela.payments.enums.PaymentStatus.REFUND_REJECTED;
import static com.adela.payments.enums.RefundDecision.REFUND_ACCEPTED;


@Service
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final RedisTemplate<String, String> redisTemplate;

    public PaymentService(PaymentRepository paymentRepository, PaymentMapper paymentMapper, RedisTemplate<String, String> redisTemplate) {
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentResponse initiatePayment(CreatePaymentRequest paymentRequest, String idempotencyKey) {

        String redisKey = "idempotency:" + idempotencyKey;
        String existingPaymentId = redisTemplate.opsForValue().get(redisKey);
        if (existingPaymentId != null) {
            Optional<Payment> existingPayment = paymentRepository.findById(Long.parseLong(existingPaymentId));
            if (existingPayment.isPresent()) {
                return paymentMapper.toResponse(existingPayment.get());
            }
            else redisTemplate.delete(redisKey);
        }
        if (idempotencyKey == null) {
            String rawKey = //userId +
                    "|" + paymentRequest.amount() + "|" + paymentRequest.method();
            String fallBackKey = "fallback" + DigestUtils.md5DigestAsHex(rawKey.getBytes());
            Boolean successKey = redisTemplate.opsForValue().setIfAbsent(fallBackKey, rawKey, Duration.ofSeconds(10));
            if (Boolean.FALSE.equals(successKey)) {
                throw new BadRequestException("Invalid idempotency key");
            }
        }

        Payment payment = paymentMapper.toEntity(paymentRequest);
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);
        redisTemplate.opsForValue().set(redisKey, payment.getId().toString(), Duration.ofMinutes(5)
        );

        return paymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        Payment payment =  paymentRepository.findById(id).orElseThrow(
                () -> new NotFoundException("Payment with id " + id + " not found"));

        return paymentMapper.toResponse(payment);
    }

    public Page<PaymentResponse> getAllPaymentsByFilter(CustomPageRequest pageRequest, String filter) {
        Set<String> ALLOWED = Set.of(
                "id", "amount", "status", "method", "reference", "createdAt"
        );
        Specification<Payment> specification = SpecificationBuilder.buildSpecification(filter, ALLOWED);
        log.info("Specification: {}", specification);
        Pageable pageable = PageUtil.toPageable(pageRequest, ALLOWED);
        Page<Payment> paymentResponse = paymentRepository.findAll(specification, pageable);
        return paymentResponse.map(paymentMapper::toResponse);
    }

    @Transactional
    public PaymentResponse requestRefund(Long id) {
        Payment payment = paymentRepository.findById(id).orElseThrow(
                () -> new NotFoundException("Payment with id " + id + " not found"));

        if (payment.getStatus() == REFUNDED){
            throw new ConflictException("Payment already refunded");
        }

        if (payment.getStatus() == PaymentStatus.REFUND_REQUESTED){
            throw new ConflictException("Refund already requested. Please wait for the refund to be processed.");
        }

        if (payment.getStatus() == REFUND_REJECTED){
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
    public PaymentResponse cancelOrApproveRefund(Long id, RefundDecision refundResponse) {
        Payment payment = paymentRepository.findById(id).orElseThrow(
                () -> new NotFoundException("Payment with id " + id + " not found"));

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
}
