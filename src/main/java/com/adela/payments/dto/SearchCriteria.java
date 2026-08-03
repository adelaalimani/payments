package com.adela.payments.dto;

import com.adela.payments.enums.FilterOperation;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SearchCriteria {

    private String key;
    private FilterOperation operation;
    private String value;
}
