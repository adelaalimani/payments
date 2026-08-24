package com.adela.payments.integration;

import com.adela.payments.entity.Payment;
import com.adela.payments.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookIntegrationTest extends AbstractIntegrationTest {

    // Matches webhook.secret in application.yaml, which the dynamic property overrides don't touch.
    private static final String WEBHOOK_SECRET = "secret";

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String createPendingPaymentReference() throws Exception {
        String token = registerAndLogin("webhook-" + System.nanoTime() + "@gmail.com", "+1200" + (System.nanoTime() % 10000000L));
        String response = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":15.00,\"method\":\"BANK_TRANSFER\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("reference").asText();
    }

    @Test
    void handlePaymentWebhook_validSignatureCompleted_updatesPaymentStatus() throws Exception {
        String reference = createPendingPaymentReference();
        String payload = "{\"reference\":\"" + reference + "\",\"status\":\"COMPLETED\"}";

        mockMvc.perform(post("/api/v1/webhook/payment")
                        .header("X-Signature", sign(payload))
                        .content(payload))
                .andExpect(status().isOk());

        Payment payment = paymentRepository.findByReference(reference).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void handlePaymentWebhook_validSignatureFailed_updatesPaymentStatus() throws Exception {
        String reference = createPendingPaymentReference();
        String payload = "{\"reference\":\"" + reference + "\",\"status\":\"DECLINED\"}";

        mockMvc.perform(post("/api/v1/webhook/payment")
                        .header("X-Signature", sign(payload))
                        .content(payload))
                .andExpect(status().isOk());

        Payment payment = paymentRepository.findByReference(reference).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void handlePaymentWebhook_invalidSignature_returnsUnauthorized() throws Exception {
        String reference = createPendingPaymentReference();
        String payload = "{\"reference\":\"" + reference + "\",\"status\":\"COMPLETED\"}";

        mockMvc.perform(post("/api/v1/webhook/payment")
                        .header("X-Signature", "clearly-wrong-signature")
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void handlePaymentWebhook_unknownReference_returnsBadRequest() throws Exception {
        String payload = "{\"reference\":\"does-not-exist\",\"status\":\"COMPLETED\"}";

        mockMvc.perform(post("/api/v1/webhook/payment")
                        .header("X-Signature", sign(payload))
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void handlePaymentWebhook_unrecognizedStatus_returnsNotFound() throws Exception {
        String reference = createPendingPaymentReference();
        String payload = "{\"reference\":\"" + reference + "\",\"status\":\"WEIRD_STATUS\"}";

        mockMvc.perform(post("/api/v1/webhook/payment")
                        .header("X-Signature", sign(payload))
                        .content(payload))
                .andExpect(status().isNotFound());
    }

    @Test
    void handlePaymentWebhook_nonPendingPayment_returnsServerError() throws Exception {
        String reference = createPendingPaymentReference();
        Payment payment = paymentRepository.findByReference(reference).orElseThrow();
        payment.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);

        String payload = "{\"reference\":\"" + reference + "\",\"status\":\"COMPLETED\"}";

        mockMvc.perform(post("/api/v1/webhook/payment")
                        .header("X-Signature", sign(payload))
                        .content(payload))
                .andExpect(status().is5xxServerError());
    }
}
