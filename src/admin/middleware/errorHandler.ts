import type { Request, Response, NextFunction } from "express";

export function errorHandler(err: Error, _req: Request, res: Response, _next: NextFunction): void {
  console.error("Admin API error:", err.message);
  res.status(500).json({
    success: false,
    code: "SYSTEM_UNEXPECTED",
    message: "서버 내부 오류가 발생했습니다.",
  });
}
