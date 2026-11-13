package com.green.keyboard_project.controller;

import com.green.keyboard_project.dto.ApiResponse;
import com.green.keyboard_project.dto.ProductResponse;
import com.green.keyboard_project.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products") // 상품 관련 공통 API 엔드포인트 접두사 (REST 표준 복수형)
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService; // 상품 비즈니스 로직 주입

    /**
     * 1. 상품 전체 목록 페이징 및 정렬 조회 (GET /api/products)
     *
     * @param page  요청할 페이지 번호 (0부터 시작, 기본값: 0)
     * @param size  한 페이지에 보여줄 상품 개수 (기본값: 10)
     * @param order 정렬 기준 (기본값: "latest" 최신등록순, "category" 카테고리순)
     * @return 페이징된 상품 목록 및 메타데이터를 ApiResponse로 감싸서 반환
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "latest") String order
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(productService.getAllProduct(page, size, order))
        );
    }

    /**
     * 2. 상품 단건 상세 조회 (GET /api/products/{productId})
     *
     * @param productId 조회할 상품 ID
     * @return 상품 상세 정보 DTO
     */
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @PathVariable int productId
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(productService.getProductById(productId))
        );
    }
}
