package com.accounting.dto;

public record ImportRowErrorDTO(int rowNumber, String field, String message) {}
