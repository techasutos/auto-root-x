package com.autorootx.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TrivyServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void readsVulnerabilitiesFromSidecarReportFile() throws Exception {
        Path report = tempDir.resolve("report.json");
        Files.writeString(report, """
                {
                  "ArtifactName": "us-central1-docker.pkg.dev/demo/api:latest",
                  "Results": [{
                    "Vulnerabilities": [{
                      "VulnerabilityID": "CVE-2023-38545",
                      "PkgName": "curl",
                      "InstalledVersion": "7.88.1",
                      "FixedVersion": "8.4.0",
                      "Severity": "CRITICAL",
                      "Title": "curl SOCKS5 heap buffer overflow"
                    }]
                  }]
                }
                """);

        TrivyService service = new TrivyService();
        ReflectionTestUtils.setField(service, "trivyEnabled", true);
        ReflectionTestUtils.setField(service, "trivyReportPath", report.toString());
        ReflectionTestUtils.setField(service, "requireImageMatch", true);

        TrivyService.TrivyScanResult result = service.scanImage("us-central1-docker.pkg.dev/demo/api:latest");

        assertThat(result.available()).isTrue();
        assertThat(result.vulnerabilities()).hasSize(1);
        assertThat(result.vulnerabilities().get(0).id).isEqualTo("CVE-2023-38545");
        assertThat(result.vulnerabilities().get(0).affectedPackage).isEqualTo("curl");
    }

    @Test
    void rejectsMismatchedReportWhenImageMatchIsRequired() throws Exception {
        Path report = tempDir.resolve("mismatch.json");
        Files.writeString(report, """
                {
                  "ArtifactName": "different/image:latest",
                  "Results": []
                }
                """);

        TrivyService service = new TrivyService();
        ReflectionTestUtils.setField(service, "trivyEnabled", true);
        ReflectionTestUtils.setField(service, "trivyReportPath", report.toString());
        ReflectionTestUtils.setField(service, "requireImageMatch", true);

        TrivyService.TrivyScanResult result = service.scanImage("requested/image:latest");

        assertThat(result.available()).isFalse();
        assertThat(result.message()).contains("image mismatch");
    }
}
