package com.autorootx.plugin.impl;

import com.autorootx.model.AnalysisRequest;
import com.autorootx.model.AnalysisResult;
import com.autorootx.plugin.Analyzer;
import com.autorootx.service.OssService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OssAnalyzer implements Analyzer {

    private final OssService osv;

    public OssAnalyzer(OssService osv) {
        this.osv = osv;
    }

    public String id() { return "OSS"; }
    public String name() { return "Dependency Scanner"; }
    public String category() { return "SECURITY"; }
    public List<String> inputs() { return List.of("dependency"); }

    public AnalysisResult analyze(AnalysisRequest req) {

        try {
            String dep = (String) req.payload.get("dependency");

            String result = osv.scan(dep);

            AnalysisResult r = new AnalysisResult();
            r.summary = result;
            return r;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
