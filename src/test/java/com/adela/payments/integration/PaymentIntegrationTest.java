package com.adela.payments.integration;

import com.adela.payments.request.CreatePaymentRequest;
import com.adela.payments.response.PaymentResponse;
import com.adela.payments.enums.PaymentMethod;
import com.adela.payments.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PaymentIntegrationTest {

//    @Container
//    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
//
//    @DynamicPropertySource
//    static void configureDataSource(DynamicPropertyRegistry registry) {
//        registry.add("spring.datasource.url", postgres::getJdbcUrl);
//        registry.add("spring.datasource.username", postgres::getUsername);
//        registry.add("spring.datasource.password", postgres::getPassword);
//    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PaymentRepository paymentRepository;

    @MockitoBean
    RedisTemplate<String, String> redisTemplate;

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    @TestConfiguration
    static class TestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    @Test
    void createPayment_persistsToDatabase_andReturnsCorrectResponse() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("150.00"), PaymentMethod.CREDIT_CARD);

        MvcResult result = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "integration-key-1")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.method").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.reference").isNotEmpty())
                .andReturn();

        PaymentResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentResponse.class);
        assertThat(paymentRepository.findById(response.id())).isPresent();
    }

    @Test
    void createAndGetPayment_fullRoundTrip_returnsConsistentData() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("75.00"), PaymentMethod.BANK_TRANSFER);

        MvcResult createResult = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "integration-key-2")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        PaymentResponse created = objectMapper.readValue(createResult.getResponse().getContentAsString(), PaymentResponse.class);

        mockMvc.perform(get("/api/v1/payments/" + created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.id()))
                .andExpect(jsonPath("$.amount").value(75.00))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.method").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.reference").value(created.reference()));
    }

    @Test
    void getPayment_nonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/payments/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createPayment_invalidAmount_returns400_doesNotPersist() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("0.50"), PaymentMethod.CREDIT_CARD);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    void createMultiplePayments_eachPersistedWithUniqueReference() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("100.00"), PaymentMethod.CREDIT_CARD);

        MvcResult r1 = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-A")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn();

        MvcResult r2 = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-B")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn();

        PaymentResponse p1 = objectMapper.readValue(r1.getResponse().getContentAsString(), PaymentResponse.class);
        PaymentResponse p2 = objectMapper.readValue(r2.getResponse().getContentAsString(), PaymentResponse.class);

        assertThat(p1.reference()).isNotEqualTo(p2.reference());
        assertThat(paymentRepository.count()).isEqualTo(2);
    }
}