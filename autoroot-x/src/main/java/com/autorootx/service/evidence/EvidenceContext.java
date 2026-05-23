package com.autorootx.service.evidence;

import com.autorootx.model.AgentRequest;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class EvidenceContext {
    private final AgentRequest request;
    private final String searchableText;

    public EvidenceContext(AgentRequest request) {
        this.request = request;
        this.searchableText = (safe(request.problem) + " " + safe(request.context) + " " + safe(request.hints))
                .toLowerCase(Locale.ROOT);
    }

    public AgentRequest request() {
        return request;
    }

    public String searchableText() {
        return searchableText;
    }

    public boolean containsAny(String... needles) {
        for (String needle : needles) {
            if (searchableText.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public Optional<Object> hint(String key) {
        if (request.hints == null) {
            return Optional.empty();
        }

        for (Map.Entry<String, Object> entry : request.hints.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return Optional.ofNullable(entry.getValue());
            }
        }
        return Optional.empty();
    }

    public Optional<String> hintString(String key) {
        return hint(key)
                .map(String::valueOf)
                .filter(s -> !s.isBlank());
    }

    public boolean requestedSource(String sourceId) {
        Optional<Object> requested = hint("sources").or(() -> hint("selectedSources"));
        if (requested.isEmpty()) {
            return false;
        }

        Object value = requested.get();
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf)
                    .anyMatch(s -> s.equalsIgnoreCase(sourceId));
        }
        return String.valueOf(value).toLowerCase(Locale.ROOT).contains(sourceId.toLowerCase(Locale.ROOT));
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
