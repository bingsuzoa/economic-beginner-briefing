package com.economicbriefing.briefing;

import com.economicbriefing.article.ArticleEntity;
import com.economicbriefing.article.ParagraphSplitter;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded, verbatim source context for the existing selection call, never a generated summary. */
final class SelectionEvidence {
    private static final int MAX_CHARS = 900;
    private static final Pattern RELATION = Pattern.compile(
            "때문|영향|반면|다만|하지만|따라|부담|우려|전망|예상|신뢰|물가|금리|환율|수요|공급|차환|예금|조건|경우|의사록|계약통화|자산|자금|매각|보유|회수|의존|통항|달러|엔화|수출|제재|매입|준비금");
    private SelectionEvidence() {}

    static String excerpt(ArticleEntity article, ParagraphSplitter splitter) {
        return excerpt(article, splitter, MAX_CHARS);
    }

    static String excerpt(ArticleEntity article, ParagraphSplitter splitter, int maxChars) {
        int budget = Math.max(0, Math.min(MAX_CHARS, maxChars));
        if (!"FULL_TEXT".equals(article.getBodyStatus()) || article.getBody() == null) return "원문 미확보";
        var usable = splitter.split(article.getBody()).entrySet().stream()
                .filter(p -> splitter.usableEvidence(p.getValue())).toList();
        if (usable.isEmpty()) return "원문 근거 없음";
        // Keep the lead for subject/time, then distinct complete paragraphs that explain the observation.
        var ranked = new ArrayList<>(usable);
        ranked.sort(Comparator.<Map.Entry<String,String>>comparingInt(p -> score(p.getValue())).reversed());
        var picked = new LinkedHashMap<String,String>();
        int chars = add(picked, usable.getFirst(), 0, budget);
        for (var p : ranked) if (!picked.containsKey(p.getKey())) chars = add(picked,p,chars,budget);
        // Return original source order; ranking is only packing, not an importance verdict.
        return usable.stream().filter(p -> picked.containsKey(p.getKey()))
                .map(p -> p.getKey()+": "+p.getValue()).collect(java.util.stream.Collectors.joining(" | "));
    }
    private static int add(Map<String,String> picked, Map.Entry<String,String> p, int chars, int budget) {
        int size = p.getKey().length()+p.getValue().length()+5;
        if (chars+size <= budget) { picked.put(p.getKey(),p.getValue()); return chars+size; }
        return chars; // Never cut a sentence or silently summarize a long paragraph.
    }
    private static int score(String text) {
        return (int) RELATION.matcher(text).results().map(m -> m.group()).distinct().count();
    }
}
