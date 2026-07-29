package com.adela.payments.service;

import com.adela.payments.request.CreatePaymentRequest;
import com.adela.payments.response.PaymentResponse;
import com.adela.payments.entity.Payment;
import com.adela.payments.enums.PaymentStatus;
import com.adela.payments.exception.BadRequestException;
import com.adela.payments.exception.NotFoundException;
import com.adela.payments.mapper.PaymentMapper;
import com.adela.payments.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.time.Duration;
import java.util.Optional;


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


}
