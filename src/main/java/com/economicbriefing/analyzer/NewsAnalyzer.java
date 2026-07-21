package com.economicbriefing.analyzer;

import com.economicbriefing.analyzer.dto.AnalyzeNewsRequest;
import com.economicbriefing.analyzer.dto.AnalyzeNewsResult;

public interface NewsAnalyzer {

    AnalyzeNewsResult analyze(AnalyzeNewsRequest request);
}
