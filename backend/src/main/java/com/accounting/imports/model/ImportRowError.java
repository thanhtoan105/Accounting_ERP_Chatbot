package com.accounting.imports.model;

public record ImportRowError(int rowNumber, String field, String message) {}
