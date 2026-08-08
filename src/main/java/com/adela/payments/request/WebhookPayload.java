package com.adela.payments.request;

import lombok.Data;

@Data
public class WebhookPayload {

    private String reference;
    private String status;
}
