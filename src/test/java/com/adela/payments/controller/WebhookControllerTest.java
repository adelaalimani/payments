package com.adela.payments.controller;

import com.adela.payments.exception.ExceptionAdvice;
import com.adela.payments.exception.UnAuthorizedException;
import com.adela.payments.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock
    private WebhookService webhookService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WebhookController(webhookService))
                .setControllerAdvice(new ExceptionAdvice())
                .build();
    }

    @Test
    void handlePaymentWebhook_validRequest_returnsOkAndDelegatesToService() throws Exception {
        String payload = "{\"reference\":\"ref-1\",\"status\":\"COMPLETED\"}";

        mockMvc.perform(post("/api/v1/webhook/payment")
                        .header("X-Signature", "some-signature")
                        .content(payload))
                .andExpect(status().isOk());

        verify(webhookService).processWebhook(payload, "some-signature");
    }

    @Test
    void handlePaymentWebhook_missingSignatureHeader_returns500ViaCatchAllHandler() throws Exception {
        // ExceptionAdvice's catch-all Exception handler intercepts the missing-header binding
        // exception before Spring's default 400 handling would otherwise apply.
        String payload = "{\"reference\":\"ref-1\",\"status\":\"COMPLETED\"}";

        mockMvc.perform(post("/api/v1/webhook/payment").content(payload))
                .andExpect(status().isInternalServerError());

        verify(webhookService, org.mockito.Mockito.never()).processWebhook(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handlePaymentWebhook_serviceRejectsSignature_returns401() throws Exception {
        String payload = "{\"reference\":\"ref-1\",\"status\":\"COMPLETED\"}";
        doThrow(new UnAuthorizedException("Invalid signature"))
                .when(webhookService).processWebhook(payload, "bad-signature");

        mockMvc.perform(post("/api/v1/webhook/payment")
                        .header("X-Signature", "bad-signature")
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }
}
