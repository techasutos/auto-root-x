package com.autorootx.service.evidence;

import com.autorootx.model.EvidenceFinding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

final class EvidenceSupport {
    private EvidenceSupport() {}

    static EvidenceFinding finding(String source, String category, String title, String severity, String resource, String summary, String raw) {
        EvidenceFinding finding = new EvidenceFinding();
        finding.source = source;
        finding.category = category;
        finding.title = title;
        finding.severity = severity;
        finding.resource = resource;
        finding.summary = summary;
        finding.raw = raw;
        return finding;
    }

    static List<String> toStrings(Object value) {
        List<String> values = new ArrayList<>();
        if (value == null) {
            return values;
        }
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                values.addAll(toStrings(item));
            }
            return values;
        }
        if (value instanceof Map<?, ?> map) {
            values.add(map.toString());
            return values;
        }
        String text = String.valueOf(value);
        if (!text.isBlank()) {
            values.add(text);
        }
        return values;
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
