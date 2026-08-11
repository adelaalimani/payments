package com.adela.payments.controller;

import com.adela.payments.request.CreatePaymentRequest;
import com.adela.payments.response.PaymentResponse;
import com.adela.payments.enums.PaymentMethod;
import com.adela.payments.enums.PaymentStatus;
import com.adela.payments.exception.NotFoundException;
import com.adela.payments.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean
    PaymentService paymentService;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Test
    void initiatePayment_validRequest_returns200WithPayloadFields() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("100.00"), PaymentMethod.CREDIT_CARD);
        PaymentResponse response = new PaymentResponse(1L, new BigDecimal("100.00"), PaymentStatus.PENDING, PaymentMethod.CREDIT_CARD, "ref-1", null);

        when(paymentService.initiatePayment(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.method").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.reference").value("ref-1"));
    }

    @Test
    void initiatePayment_withIdempotencyKeyHeader_passesKeyToService() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("100.00"), PaymentMethod.CREDIT_CARD);
        PaymentResponse response = new PaymentResponse(1L, new BigDecimal("100.00"), PaymentStatus.PENDING, PaymentMethod.CREDIT_CARD, "ref-1", null);

        when(paymentService.initiatePayment(any(), anyString())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "my-unique-key")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void initiatePayment_amountBelowMinimum_returns400() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("0.50"), PaymentMethod.CREDIT_CARD);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Value must be greater than 1"));
    }

    @Test
    void initiatePayment_nullAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": null, \"method\": \"CREDIT_CARD\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void initiatePayment_nullMethod_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00, \"method\": null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPaymentById_found_returns200() throws Exception {
        PaymentResponse response = new PaymentResponse(5L, new BigDecimal("200.00"), PaymentStatus.COMPLETED, PaymentMethod.BANK_TRANSFER, "ref-5", null);
        when(paymentService.getPaymentById(5L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.method").value("BANK_TRANSFER"));
    }

    @Test
    void getPaymentById_notFound_returns404WithMessage() throws Exception {
        when(paymentService.getPaymentById(99L)).thenThrow(new NotFoundException("Payment with id 99 not found"));

        mockMvc.perform(get("/api/v1/payments/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Payment with id 99 not found"));
    }
}