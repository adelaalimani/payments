package com.adela.payments.controller;

import com.adela.payments.config.AnalyticsResponse;
import com.adela.payments.enums.PaymentMethod;
import com.adela.payments.enums.PaymentStatus;
import com.adela.payments.enums.RefundDecision;
import com.adela.payments.exception.ExceptionAdvice;
import com.adela.payments.exception.ForbiddenException;
import com.adela.payments.exception.NotFoundException;
import com.adela.payments.request.CreatePaymentRequest;
import com.adela.payments.request.CustomPageRequest;
import com.adela.payments.response.PaymentResponse;
import com.adela.payments.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.SpringDataJacksonConfiguration;
import org.springframework.data.web.config.SpringDataWebSettings;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        objectMapper.findAndRegisterModules();
        // Page/Sort don't serialize with plain Jackson outside a full Spring Boot context; register
        // the same module Spring Boot's autoconfiguration would normally contribute.
        objectMapper.registerModule(new SpringDataJacksonConfiguration.PageModule(
                new SpringDataWebSettings(EnableSpringDataWebSupport.PageSerializationMode.DIRECT)));
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(paymentService))
                .setControllerAdvice(new ExceptionAdvice())
                .setMessageConverters(
                        new org.springframework.http.converter.StringHttpMessageConverter(),
                        new org.springframework.http.converter.ResourceHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private PaymentResponse samplePayment(Long id) {
        return new PaymentResponse(id, BigDecimal.TEN, PaymentStatus.PENDING, PaymentMethod.CREDIT_CARD, "ref-" + id, LocalDateTime.now());
    }

    // ---------- initiatePayment ----------

    @Test
    void initiatePayment_validRequest_returnsOk() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest(BigDecimal.TEN, PaymentMethod.CREDIT_CARD);
        PaymentResponse response = samplePayment(1L);
        when(paymentService.initiatePayment(eq(request), isNull())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.reference").value("ref-1"));
    }

    @Test
    void initiatePayment_withIdempotencyKeyHeader_passesKeyToService() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest(BigDecimal.TEN, PaymentMethod.CREDIT_CARD);
        PaymentResponse response = samplePayment(2L);
        when(paymentService.initiatePayment(eq(request), eq("abc-123"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "abc-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(paymentService).initiatePayment(eq(request), keyCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(keyCaptor.getValue()).isEqualTo("abc-123");
    }

    @Test
    void initiatePayment_missingAmount_returns400() throws Exception {
        String body = "{\"method\":\"CREDIT_CARD\"}";

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void initiatePayment_amountBelowMinimum_returns400() throws Exception {
        String body = "{\"amount\":0.50,\"method\":\"CREDIT_CARD\"}";

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void initiatePayment_missingMethod_returns400() throws Exception {
        String body = "{\"amount\":10.00}";

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ---------- getPaymentById ----------

    @Test
    void getPaymentById_found_returnsOk() throws Exception {
        when(paymentService.getPaymentById(10L)).thenReturn(samplePayment(10L));

        mockMvc.perform(get("/api/v1/payments/{id}", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void getPaymentById_notFound_returns404WithMessage() throws Exception {
        when(paymentService.getPaymentById(99L)).thenThrow(new NotFoundException("Payment with id 99 not found"));

        mockMvc.perform(get("/api/v1/payments/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Payment with id 99 not found"));
    }

    @Test
    void getPaymentById_forbidden_returns403() throws Exception {
        when(paymentService.getPaymentById(11L)).thenThrow(new ForbiddenException("You are not authorized to perform this action on this payment"));

        mockMvc.perform(get("/api/v1/payments/{id}", 11L))
                .andExpect(status().isForbidden());
    }

    // ---------- getAllPaymentsByFilter ----------

    @Test
    void getAllPaymentsByFilter_withFilterAndBody_returnsOk() throws Exception {
        Page<PaymentResponse> page = new PageImpl<>(List.of(samplePayment(1L), samplePayment(2L)));
        when(paymentService.getAllPaymentsByFilter(any(CustomPageRequest.class), eq("status:eq:PENDING"))).thenReturn(page);

        CustomPageRequest pageRequest = new CustomPageRequest();
        pageRequest.setSortBy("id");

        mockMvc.perform(post("/api/v1/payments/all")
                        .param("filter", "status:eq:PENDING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pageRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void getAllPaymentsByFilter_noBodyNoFilter_returnsOk() throws Exception {
        Page<PaymentResponse> page = new PageImpl<>(List.of(samplePayment(1L)));
        when(paymentService.getAllPaymentsByFilter(isNull(), isNull())).thenReturn(page);

        mockMvc.perform(post("/api/v1/payments/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    // ---------- exportPaymentsAsJson (CSV export) ----------

    @Test
    void exportPaymentsAsJson_returnsCsvWithAttachmentHeaders() throws Exception {
        byte[] csv = "id,amount,status,method,reference,createdDate\n1,10,PENDING,CREDIT_CARD,ref-1,\n".getBytes();
        when(paymentService.exportPayments(null)).thenReturn(new org.springframework.core.io.ByteArrayResource(csv));

        mockMvc.perform(post("/api/v1/payments/exportCsv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=payments.csv"))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().bytes(csv));
    }

    // ---------- requestRefund ----------

    @Test
    void requestRefund_returnsOk() throws Exception {
        when(paymentService.requestRefund(5L)).thenReturn(samplePayment(5L));

        mockMvc.perform(post("/api/v1/payments/{id}/requestRefund", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    // ---------- cancelOrApproveRefund ----------

    @Test
    void cancelOrApproveRefund_accepted_returnsOk() throws Exception {
        when(paymentService.cancelOrApproveRefund(eq(6L), eq(RefundDecision.REFUND_ACCEPTED))).thenReturn(samplePayment(6L));

        mockMvc.perform(post("/api/v1/payments/{id}/refundDecision", 6L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"REFUND_ACCEPTED\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(6));
    }

    // ---------- getAnalytics ----------

    @Test
    void getAnalytics_withDateRange_returnsOk() throws Exception {
        AnalyticsResponse response = new AnalyticsResponse(5L, BigDecimal.valueOf(500), 80.0, Map.of());
        when(paymentService.getAnalytics(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/analytics")
                        .param("from", "2026-01-01T00:00:00")
                        .param("to", "2026-01-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(5))
                .andExpect(jsonPath("$.successRate").value(80.0));
    }

    @Test
    void getAnalytics_withoutDateRange_returnsOk() throws Exception {
        AnalyticsResponse response = new AnalyticsResponse(0L, BigDecimal.ZERO, 0.0, Map.of());
        when(paymentService.getAnalytics(isNull(), isNull())).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(0));
    }
}