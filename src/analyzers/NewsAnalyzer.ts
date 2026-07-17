import type { AnalyzeNewsRequest, AnalyzeNewsResult } from "../domain/analyzedNews.js";

export interface NewsAnalyzer {
  analyze(request: AnalyzeNewsRequest): Promise<AnalyzeNewsResult>;
}
