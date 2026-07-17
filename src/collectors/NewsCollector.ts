import type { CollectNewsRequest, CollectNewsResult } from "../domain/article.js";

export interface NewsCollector {
  collect(request: CollectNewsRequest): Promise<CollectNewsResult>;
}
