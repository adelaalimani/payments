package com.adela.payments.service;

import com.adela.payments.dto.CreatePaymentRequest;
import com.adela.payments.dto.PaymentResponse;
import com.adela.payments.entity.Payment;
import com.adela.payments.enums.PaymentStatus;
import com.adela.payments.expection.NotFoundException;
import com.adela.payments.mapper.PaymentMapper;
import com.adela.payments.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    public PaymentService(PaymentRepository paymentRepository, PaymentMapper paymentMapper) {
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentResponse initiatePayment(CreatePaymentRequest paymentRequest) {

        Payment payment = paymentMapper.toEntity(paymentRequest);
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);
        return paymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        Payment payment =  paymentRepository.findById(id).orElseThrow(
                () -> new NotFoundException("Payment with id " + id + " not found"));

        return paymentMapper.toResponse(payment);
    }


}
