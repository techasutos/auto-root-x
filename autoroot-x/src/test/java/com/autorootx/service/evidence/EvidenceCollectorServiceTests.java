package com.autorootx.service.evidence;

import com.autorootx.model.AgentRequest;
import com.autorootx.model.EvidenceBundle;
import com.autorootx.model.EvidenceFinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceCollectorServiceTests {

    @Test
    void manualSourcesForceOnlyRequestedEvidenceSources() {
        EvidenceCollectorService collector = new EvidenceCollectorService(List.of(
                source("cloud-logging", true),
                source("trivy", false),
                source("osv", false)
        ));

        AgentRequest request = new AgentRequest();
        request.problem = "generic issue";
        request.hints = Map.of("sources", List.of("trivy", "osv"));

        EvidenceBundle bundle = collector.collect(request);

        assertThat(bundle.selectedSources).containsExactly("osv", "trivy");
        assertThat(bundle.findings).extracting(f -> f.source).containsExactly("osv", "trivy");
    }

    private EvidenceSource source(String id, boolean supports) {
        return new EvidenceSource() {
            @Override public String id() { return id; }
            @Override public String label() { return id; }
            @Override public String category() { return "TEST"; }
            @Override public boolean supports(EvidenceContext context) { return supports; }

            @Override
            public List<EvidenceFinding> collect(EvidenceContext context) {
                EvidenceFinding finding = new EvidenceFinding();
                finding.source = id;
                finding.category = "TEST";
                finding.title = id + " finding";
                finding.severity = "LOW";
                return List.of(finding);
            }
        };
    }
}
