package com.adela.payments.service;

import com.adela.payments.entity.Payment;
import com.adela.payments.enums.PaymentStatus;
import com.adela.payments.exception.BadRequestException;
import com.adela.payments.exception.NotFoundException;
import com.adela.payments.exception.UnAuthorizedException;
import com.adela.payments.repository.PaymentRepository;
import com.adela.payments.request.WebhookPayload;
import com.adela.payments.utils.SignatureUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {

    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    @Value("${webhook.secret}")
    private String webhookSecret;

    public WebhookService(PaymentRepository paymentRepository, ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void processWebhook(String payload, String signature) {

        if (!SignatureUtil.isValidSignature(payload, signature, webhookSecret)) {
            throw new UnAuthorizedException("Invalid signature");
        }

        WebhookPayload webhookPayload;
        try {
            webhookPayload = objectMapper.readValue(payload, WebhookPayload.class);
        } catch (Exception e) {
            throw new BadRequestException("Failed to parse webhook payload: " + e.getMessage());
        }

        Payment payment = paymentRepository.findByReference(webhookPayload.getReference())
                .orElseThrow(() -> new BadRequestException("Payment not found for reference: " + webhookPayload.getReference()));
        PaymentStatus newStatus = mapExternalStatus(webhookPayload.getStatus());

        if (payment.getStatus() == newStatus) {
            return;
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot update payment from status " + payment.getStatus() + " via webhook");
        }

        payment.setStatus(newStatus);
        paymentRepository.save(payment);
    }

    private PaymentStatus mapExternalStatus(String externalStatus) {
        return switch (externalStatus.toUpperCase()) {
            case "COMPLETED", "SUCCEEDED" -> PaymentStatus.COMPLETED;
            case "FAILED", "DECLINED" -> PaymentStatus.FAILED;
            default -> throw new NotFoundException("Unrecognized webhook status: " + externalStatus);
        };
    }

}
