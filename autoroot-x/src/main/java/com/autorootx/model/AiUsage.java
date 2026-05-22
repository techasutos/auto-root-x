package com.autorootx.model;

public class AiUsage {
    public String provider;
    public String model;

    public Integer callCount;
    public Long latencyMs;
    public Integer retries;

    public Integer inputTokens;
    public Integer outputTokens;
    public Integer totalTokens;
    public Double estimatedCostUsd;

    public String errorClass;
    public Boolean rateLimited;
    public Boolean circuitOpen;
}
