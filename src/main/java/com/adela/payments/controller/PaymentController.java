package com.adela.payments.controller;

import com.adela.payments.request.CreatePaymentRequest;
import com.adela.payments.response.PaymentResponse;
import com.adela.payments.service.PaymentService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@Slf4j
@Validated
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> initiatePayment(@Valid @RequestBody CreatePaymentRequest paymentRequest,
                                                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey ) {
    return ResponseEntity.ok(paymentService.initiatePayment(paymentRequest, idempotencyKey));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }
}
