package com.economicbriefing.collector;

import com.economicbriefing.collector.dto.CollectNewsRequest;
import com.economicbriefing.collector.dto.CollectNewsResult;

public interface NewsCollector {
    CollectNewsResult collect(CollectNewsRequest request);
}
