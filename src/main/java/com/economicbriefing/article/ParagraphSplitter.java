package com.economicbriefing.article;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ParagraphSplitter {
    private static final Pattern EMAIL = Pattern.compile(".*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*");

    public Map<String, String> split(String body) {
        Map<String, String> result = new LinkedHashMap<>();
        if (body == null) return result;
        int number = 0;
        for (String raw : body.split("\\R+")) {
            String text = raw.strip();
            if (text.isEmpty()) continue;
            result.put("P%03d".formatted(++number), text);
        }
        return result;
    }

    public boolean usableEvidence(String text) {
        if (text == null || text.isBlank()) return false;
        String value = text.strip();
        return !value.startsWith("[")
                && !value.contains("무단 전재")
                && !value.contains("AI 학습")
                && !EMAIL.matcher(value).matches();
    }

    public List<Map.Entry<String, String>> modelInput(Map<String, String> paragraphs, int maxChars) {
        List<Map.Entry<String, String>> result = new ArrayList<>();
        int chars = 0;
        // ponytail: very long wire stories keep the leading paragraphs; add hint-based packing
        // only if a real selected article starts losing decisive evidence beyond this ceiling.
        for (var entry : paragraphs.entrySet()) {
            int next = entry.getKey().length() + entry.getValue().length() + 2;
            if (!result.isEmpty() && chars + next > maxChars) break;
            result.add(entry);
            chars += next;
        }
        return result;
    }
}
