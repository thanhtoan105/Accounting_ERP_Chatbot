package com.accounting.imports;

import java.util.Locale;

public enum ImportType {
    CUSTOMERS("customers", "customers-template"),
    SUPPLIERS("suppliers", "suppliers-template"),
    BANK_ACCOUNTS("bank-accounts", "bank-accounts-template"),
    OPENING_BALANCES("opening-balances", "opening-balances-template");

    private final String pathSegment;
    private final String templateBaseName;

    ImportType(String pathSegment, String templateBaseName) {
        this.pathSegment = pathSegment;
        this.templateBaseName = templateBaseName;
    }

    public String getPathSegment() {
        return pathSegment;
    }

    public String getTemplateBaseName() {
        return templateBaseName;
    }

    public static ImportType fromPath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Import type is required");
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (ImportType type : values()) {
            if (type.pathSegment.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported import type: " + value);
    }
}
