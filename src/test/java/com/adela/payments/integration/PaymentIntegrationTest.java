package com.adela.payments.integration;

import com.adela.payments.entity.Payment;
import com.adela.payments.enums.PaymentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentIntegrationTest extends AbstractIntegrationTest {

    private static final String VALID_PAGE_BODY = "{\"page\":0,\"size\":10,\"sortBy\":\"id\",\"sortDirection\":\"DESC\"}";

    private long createPayment(String token) throws Exception {
        // Each call gets its own Idempotency-Key so repeated calls with the same token/amount in a
        // single test don't trip PaymentService's fallback dedup guard (same user+amount+method
        // within 60s is rejected as a likely-duplicate request when no idempotency key is given).
        String response = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":25.00,\"method\":\"CREDIT_CARD\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void setStatus(long paymentId, PaymentStatus status) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setStatus(status);
        paymentRepository.save(payment);
    }

    // ---------- initiatePayment ----------

    @Test
    void initiatePayment_asCustomer_createsPendingPayment() throws Exception {
        String token = registerAndLogin("pay-init-1@gmail.com", "+10000050001");

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":25.00,\"method\":\"CREDIT_CARD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reference").exists());
    }

    @Test
    void initiatePayment_withIdempotencyKey_duplicateRequestReturnsSamePayment() throws Exception {
        String token = registerAndLogin("pay-init-2@gmail.com", "+10000050002");
        String idempotencyKey = "test-idem-key-1";

        String first = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":25.00,\"method\":\"CREDIT_CARD\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long firstId = objectMapper.readTree(first).get("id").asLong();

        String second = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":25.00,\"method\":\"CREDIT_CARD\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long secondId = objectMapper.readTree(second).get("id").asLong();

        assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void initiatePayment_withoutAuth_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":25.00,\"method\":\"CREDIT_CARD\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void initiatePayment_amountBelowMinimum_returnsBadRequest() throws Exception {
        String token = registerAndLogin("pay-init-3@gmail.com", "+10000050003");

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0.10,\"method\":\"CREDIT_CARD\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- getPaymentById ----------

    @Test
    void getPaymentById_owner_returnsPayment() throws Exception {
        String token = registerAndLogin("pay-get-1@gmail.com", "+10000050004");
        long paymentId = createPayment(token);

        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId));
    }

    @Test
    void getPaymentById_otherCustomer_isForbidden() throws Exception {
        String ownerToken = registerAndLogin("pay-get-2@gmail.com", "+10000050005");
        long paymentId = createPayment(ownerToken);
        String otherToken = registerAndLogin("pay-get-3@gmail.com", "+10000050006");

        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPaymentById_admin_canAccessAnyPayment() throws Exception {
        String ownerToken = registerAndLogin("pay-get-4@gmail.com", "+10000050007");
        long paymentId = createPayment(ownerToken);
        String adminToken = registerAndLogin("pay-get-admin-1@gmail.com", "+10000050008");
        promoteToAdmin("pay-get-admin-1@gmail.com");

        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void getPaymentById_notFound_returns404() throws Exception {
        String token = registerAndLogin("pay-get-5@gmail.com", "+10000050009");

        mockMvc.perform(get("/api/v1/payments/{id}", 987654321L).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    // ---------- getAllPaymentsByFilter ----------

    @Test
    void getAllPaymentsByFilter_customer_seesOnlyOwnPayments() throws Exception {
        String tokenA = registerAndLogin("pay-list-a@gmail.com", "+10000050010");
        createPayment(tokenA);
        createPayment(tokenA);
        String tokenB = registerAndLogin("pay-list-b@gmail.com", "+10000050011");
        createPayment(tokenB);

        mockMvc.perform(post("/api/v1/payments/all")
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAGE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void getAllPaymentsByFilter_admin_seesAllPayments() throws Exception {
        String tokenA = registerAndLogin("pay-list-c@gmail.com", "+10000050012");
        createPayment(tokenA);
        String adminToken = registerAndLogin("pay-list-admin@gmail.com", "+10000050013");
        promoteToAdmin("pay-list-admin@gmail.com");

        mockMvc.perform(post("/api/v1/payments/all")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAGE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getAllPaymentsByFilter_invalidFilterField_returnsBadRequest() throws Exception {
        String token = registerAndLogin("pay-list-d@gmail.com", "+10000050014");

        mockMvc.perform(post("/api/v1/payments/all")
                        .header("Authorization", bearer(token))
                        .param("filter", "notAField:eq:1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAGE_BODY))
                .andExpect(status().isBadRequest());
    }

    // ---------- exportPaymentsAsJson ----------

    @Test
    void exportPaymentsAsJson_returnsCsvContainingPayment() throws Exception {
        String token = registerAndLogin("pay-export-1@gmail.com", "+10000050015");
        createPayment(token);

        mockMvc.perform(post("/api/v1/payments/exportCsv").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=payments.csv"))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id,amount,status,method,reference,createdDate")));
    }

    // ---------- requestRefund ----------

    @Test
    void requestRefund_completedPayment_transitionsToRefundRequested() throws Exception {
        String token = registerAndLogin("pay-refund-1@gmail.com", "+10000050016");
        long paymentId = createPayment(token);
        setStatus(paymentId, PaymentStatus.COMPLETED);

        mockMvc.perform(post("/api/v1/payments/{id}/requestRefund", paymentId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUND_REQUESTED"));
    }

    @Test
    void requestRefund_pendingPayment_returnsConflict() throws Exception {
        String token = registerAndLogin("pay-refund-2@gmail.com", "+10000050017");
        long paymentId = createPayment(token);

        mockMvc.perform(post("/api/v1/payments/{id}/requestRefund", paymentId).header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void requestRefund_otherCustomerPayment_isForbidden() throws Exception {
        String ownerToken = registerAndLogin("pay-refund-3@gmail.com", "+10000050018");
        long paymentId = createPayment(ownerToken);
        setStatus(paymentId, PaymentStatus.COMPLETED);
        String otherToken = registerAndLogin("pay-refund-4@gmail.com", "+10000050019");

        mockMvc.perform(post("/api/v1/payments/{id}/requestRefund", paymentId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    // ---------- cancelOrApproveRefund ----------

    @Test
    void cancelOrApproveRefund_adminAccepts_transitionsToRefunded() throws Exception {
        String token = registerAndLogin("pay-decision-1@gmail.com", "+10000050020");
        long paymentId = createPayment(token);
        setStatus(paymentId, PaymentStatus.COMPLETED);
        mockMvc.perform(post("/api/v1/payments/{id}/requestRefund", paymentId).header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        String adminToken = registerAndLogin("pay-decision-admin-1@gmail.com", "+10000050021");
        promoteToAdmin("pay-decision-admin-1@gmail.com");

        mockMvc.perform(post("/api/v1/payments/{id}/refundDecision", paymentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"REFUND_ACCEPTED\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void cancelOrApproveRefund_adminRejects_transitionsToRefundRejected() throws Exception {
        String token = registerAndLogin("pay-decision-2@gmail.com", "+10000050022");
        long paymentId = createPayment(token);
        setStatus(paymentId, PaymentStatus.COMPLETED);
        mockMvc.perform(post("/api/v1/payments/{id}/requestRefund", paymentId).header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        String adminToken = registerAndLogin("pay-decision-admin-2@gmail.com", "+10000050023");
        promoteToAdmin("pay-decision-admin-2@gmail.com");

        mockMvc.perform(post("/api/v1/payments/{id}/refundDecision", paymentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"REFUND_REJECTED\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUND_REJECTED"));
    }

    @Test
    void cancelOrApproveRefund_asCustomer_isForbidden() throws Exception {
        String token = registerAndLogin("pay-decision-3@gmail.com", "+10000050024");
        long paymentId = createPayment(token);
        setStatus(paymentId, PaymentStatus.COMPLETED);
        mockMvc.perform(post("/api/v1/payments/{id}/requestRefund", paymentId).header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payments/{id}/refundDecision", paymentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"REFUND_ACCEPTED\""))
                .andExpect(status().isForbidden());
    }

    // ---------- getAnalytics ----------

    @Test
    void getAnalytics_admin_returnsAggregatedData() throws Exception {
        String token = registerAndLogin("pay-analytics-1@gmail.com", "+10000050025");
        long paymentId = createPayment(token);
        setStatus(paymentId, PaymentStatus.COMPLETED);
        String adminToken = registerAndLogin("pay-analytics-admin@gmail.com", "+10000050026");
        promoteToAdmin("pay-analytics-admin@gmail.com");

        mockMvc.perform(get("/api/v1/payments/analytics").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").isNumber())
                .andExpect(jsonPath("$.successRate").isNumber());
    }

    @Test
    void getAnalytics_customer_isForbidden() throws Exception {
        String token = registerAndLogin("pay-analytics-2@gmail.com", "+10000050027");

        mockMvc.perform(get("/api/v1/payments/analytics").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllPaymentsByFilter_withoutRequestBody_isRejectedByServer() throws Exception {
        // Documents a real gap in PaymentController: CustomPageRequest is @RequestBody(required = false),
        // but PaymentService/PageUtil dereference it unconditionally, so an absent body causes a 500
        // rather than falling back to defaults.
        String token = registerAndLogin("pay-list-nobody@gmail.com", "+10000050028");

        mockMvc.perform(post("/api/v1/payments/all").header("Authorization", bearer(token)))
                .andExpect(status().is5xxServerError());
    }
}
