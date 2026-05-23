package com.autorootx.service.evidence;

import com.autorootx.model.EvidenceFinding;

import java.util.List;

public interface EvidenceSource {
    String id();
    String label();
    String category();
    boolean supports(EvidenceContext context);
    List<EvidenceFinding> collect(EvidenceContext context);
}
