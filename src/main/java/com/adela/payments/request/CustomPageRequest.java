package com.adela.payments.request;

import lombok.Data;

@Data
public class CustomPageRequest {

    private int page = 0;
    private int size = 20;
    private String sortBy = "createdAt";
    private String sortDirection = "DESC";
}
