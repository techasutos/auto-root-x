package com.autorootx.model;

import java.time.Instant;

public class ApiErrorResponse {
    public Instant timestamp = Instant.now();
    public int status;
    public String error;
    public String message;
    public String path;
}
