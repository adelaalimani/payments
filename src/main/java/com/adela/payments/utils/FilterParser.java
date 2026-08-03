package com.adela.payments.utils;

import com.adela.payments.dto.SearchCriteria;
import com.adela.payments.enums.FilterOperation;
import com.adela.payments.exception.BadRequestException;

import java.util.Arrays;
import java.util.List;

public class FilterParser {

    public static List<SearchCriteria> parse(String filter) {
        if (filter == null || filter.isBlank()) {
            return List.of();
        }

        return Arrays.stream(filter.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(FilterParser::toCriteria)
                .toList();
    }

    private static SearchCriteria toCriteria(String part) {
        String[] tokens = part.split(":", 3);
        if (tokens.length != 3) {
            throw new BadRequestException("Invalid filter format: '" + part + "'");
        }

        FilterOperation operation;
        try {
            operation = FilterOperation.valueOf(tokens[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unsupported filter operation: '" + tokens[1] + "'");
        }

        return new SearchCriteria(tokens[0], operation, tokens[2]);
    }
}
