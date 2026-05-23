package com.autorootx.plugin.impl;

import com.autorootx.model.AnalysisRequest;
import com.autorootx.model.AnalysisResult;
import com.autorootx.service.OssService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OssAnalyzerTests {

    @Test
    void scansMultipleDependenciesFromUiPayload() {
        CapturingOssService oss = new CapturingOssService();
        OssAnalyzer analyzer = new OssAnalyzer(oss);

        AnalysisRequest request = new AnalysisRequest();
        request.analyzerId = "OSS";
        request.payload = Map.of("dependencies", List.of(
                Map.of("name", "log4j-core", "version", "2.14.1"),
                Map.of("name", "lodash", "version", "4.17.19")
        ));

        AnalysisResult result = analyzer.analyze(request);

        assertThat(oss.dependencies).containsExactly("log4j-core", "lodash");
        assertThat(result.summary).contains("## log4j-core", "## lodash");
        assertThat(result.confidence).isEqualTo("OSV");
    }

    private static class CapturingOssService extends OssService {
        private final List<String> dependencies = new ArrayList<>();

        @Override
        public String scan(String dependency) {
            dependencies.add(dependency);
            return "{}";
        }
    }
}
