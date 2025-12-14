package com.accounting.integration.metabase;

public class MetabaseApiException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public MetabaseApiException(String message) {
        super(message);
        this.statusCode = 0;
        this.errorCode = "METABASE_ERROR";
    }

    public MetabaseApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = "METABASE_ERROR";
    }

    public MetabaseApiException(String message, int statusCode, String errorCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public MetabaseApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.errorCode = "METABASE_ERROR";
    }

    public MetabaseApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = "METABASE_ERROR";
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
