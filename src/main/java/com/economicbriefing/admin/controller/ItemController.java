package com.economicbriefing.admin.controller;

import com.economicbriefing.admin.dto.ApiResponse;
import com.economicbriefing.admin.repository.PipelineItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class ItemController {

    private final PipelineItemRepository itemRepo;

    public ItemController(PipelineItemRepository itemRepo) {
        this.itemRepo = itemRepo;
    }

    @GetMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<?>> getItem(@PathVariable Long itemId) {
        return itemRepo.findById(itemId)
                .<ResponseEntity<ApiResponse<?>>>map(item -> ResponseEntity.ok(ApiResponse.ok(item)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("ITEM_NOT_FOUND", "뉴스 항목을 찾을 수 없습니다.")));
    }
}
