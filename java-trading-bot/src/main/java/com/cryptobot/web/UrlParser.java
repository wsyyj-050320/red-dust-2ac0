package com.cryptobot.web;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class UrlParser {
    private UrlParser() {
    }

    static Map<String, List<String>> parse(String query) {
        Map<String, List<String>> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            String key = idx > 0 ? decode(pair.substring(0, idx)) : decode(pair);
            String value = idx > 0 && pair.length() > idx + 1 ? decode(pair.substring(idx + 1)) : "";
            params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
