package com.adela.payments.controller;

import com.adela.payments.service.WebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhook")
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/payment")
    public ResponseEntity<Void> handlePaymentWebhook(
            @RequestBody String webhookPayload,
            @RequestHeader("X-Signature") String signature) {
        log.info("Received payment webhook: {}", webhookPayload);
        webhookService.processWebhook(webhookPayload, signature);
        return ResponseEntity.ok().build();
    }
}
