package com.green.keyboard_project.controller;

import com.green.keyboard_project.dto.ApiResponse;
import com.green.keyboard_project.dto.product.ProductListResponseDto;
import com.green.keyboard_project.dto.product.ProductResponseDto;
import com.green.keyboard_project.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<ProductListResponseDto>> getProducts(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "subcategory", required = false) String subcategory,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "16") int size
    ) {
        ProductListResponseDto response = productService.getProducts(category, subcategory, page, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponseDto>> getProductById(@PathVariable("id") int id) {
        ProductResponseDto response = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
