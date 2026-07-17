import type { Request, Response, NextFunction } from "express";

export function createAuthMiddleware(adminToken: string) {
  return (req: Request, res: Response, next: NextFunction): void => {
    if (!adminToken) {
      next();
      return;
    }

    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      res.status(401).json({
        success: false,
        code: "UNAUTHORIZED",
        message: "인증 토큰이 필요합니다.",
      });
      return;
    }

    const token = authHeader.slice(7);
    if (token !== adminToken) {
      res.status(401).json({
        success: false,
        code: "UNAUTHORIZED",
        message: "유효하지 않은 인증 토큰입니다.",
      });
      return;
    }

    next();
  };
}
