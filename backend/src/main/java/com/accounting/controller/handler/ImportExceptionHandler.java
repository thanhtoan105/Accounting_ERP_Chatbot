package com.accounting.controller.handler;

import com.accounting.dto.ImportResultDTO;
import com.accounting.dto.ImportRowErrorDTO;
import com.accounting.imports.exception.ImportValidationException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.accounting.controller")
public class ImportExceptionHandler {

    @ExceptionHandler(ImportValidationException.class)
    public ResponseEntity<ImportResultDTO> handleValidation(ImportValidationException exception) {
        List<ImportRowErrorDTO> errors = exception.getErrors().stream()
                .map(
                        error -> new ImportRowErrorDTO(
                                error.rowNumber(), error.field(), error.message()))
                .collect(Collectors.toList());

        ImportResultDTO body = new ImportResultDTO(0, 0, errors.size(), errors, exception.getReportId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
