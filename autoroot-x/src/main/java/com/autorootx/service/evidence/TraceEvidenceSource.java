package com.autorootx.service.evidence;

import com.autorootx.model.EvidenceFinding;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TraceEvidenceSource implements EvidenceSource {
    @Override public String id() { return "trace"; }
    @Override public String label() { return "Cloud Trace"; }
    @Override public String category() { return "OBSERVABILITY"; }

    @Override
    public boolean supports(EvidenceContext context) {
        return context.hint("trace").isPresent()
                || context.hint("traces").isPresent()
                || context.containsAny("trace", "span", "request id", "latency", "slow request");
    }

    @Override
    public List<EvidenceFinding> collect(EvidenceContext context) {
        String raw = EvidenceSupport.firstNonBlank(
                context.hint("trace").map(String::valueOf).orElse(""),
                context.hint("traces").map(String::valueOf).orElse("")
        );

        return List.of(EvidenceSupport.finding(
                id(),
                category(),
                "Trace context",
                raw.toLowerCase().contains("error") ? "HIGH" : "LOW",
                context.hintString("traceId").orElse(""),
                raw.isBlank()
                        ? "Problem mentions traces or latency. Connect Cloud Trace API for span-level evidence."
                        : "Trace evidence supplied in request hints.",
                raw
        ));
    }
}
