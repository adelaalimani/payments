package com.adela.payments.controller;

import com.adela.payments.enums.RefundDecision;
import com.adela.payments.request.CreatePaymentRequest;
import com.adela.payments.request.CustomPageRequest;
import com.adela.payments.response.PaymentResponse;
import com.adela.payments.service.PaymentService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<PaymentResponse> initiatePayment(@Valid @RequestBody CreatePaymentRequest paymentRequest,
                                                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey ) {
    return ResponseEntity.ok(paymentService.initiatePayment(paymentRequest, idempotencyKey));
    }

    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @PostMapping("/all")
    public ResponseEntity<Page<PaymentResponse>> getAllPaymentsByFilter(
            @RequestParam(required = false) String filter,
            @RequestBody(required = false) CustomPageRequest pageRequest) {
        return ResponseEntity.ok(paymentService.getAllPaymentsByFilter(pageRequest, filter));
    }

    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @PostMapping("/exportCsv")
    public ResponseEntity<Resource> exportPaymentsAsJson(@RequestParam(required = false) String filter){
       Resource resource = paymentService.exportPayments(filter);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payments.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(resource);
    }

    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @PostMapping("/{id}/requestRefund")
    public ResponseEntity<PaymentResponse> requestRefund(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.requestRefund(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/refundDecision")
    public ResponseEntity<PaymentResponse> cancelOrApproveRefund(@PathVariable Long id, @RequestBody RefundDecision decision) {
        return ResponseEntity.ok(paymentService.cancelOrApproveRefund(id, decision));
    }
}
