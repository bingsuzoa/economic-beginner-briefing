import { Router } from "express";
import type { PipelineItemRepository } from "../../db/repositories/PipelineItemRepository.js";

export function createItemRoutes(itemRepo: PipelineItemRepository): Router {
  const router = Router();

  router.get("/items/:itemId", async (req, res, next) => {
    try {
      const itemId = parseInt(req.params.itemId!, 10);
      if (isNaN(itemId)) {
        res.status(400).json({
          success: false,
          code: "INVALID_PARAMETER",
          message: "유효하지 않은 항목 ID입니다.",
        });
        return;
      }

      const item = await itemRepo.findById(itemId);
      if (!item) {
        res.status(404).json({
          success: false,
          code: "ITEM_NOT_FOUND",
          message: "뉴스 항목을 찾을 수 없습니다.",
        });
        return;
      }

      res.json({ success: true, data: item });
    } catch (err) {
      next(err);
    }
  });

  return router;
}
