package com.autorootx.service;

import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;

@Service
public class OssService {

    public String scan(String dependency) throws Exception {

        URL url = new URL("https://api.osv.dev/v1/query");

        HttpURLConnection conn =
                (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        String body = """
        {
          "package": {
            "name": "%s",
            "ecosystem": "Maven"
          }
        }
        """.formatted(dependency);

        conn.getOutputStream().write(body.getBytes());

        return new String(conn.getInputStream().readAllBytes());
    }
}
