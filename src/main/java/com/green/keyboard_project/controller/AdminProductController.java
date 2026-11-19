package com.green.keyboard_project.controller;

import com.green.keyboard_project.dto.ApiResponse;
import com.green.keyboard_project.dto.CreateProductRequest;
import com.green.keyboard_project.dto.UpdateProductRequest;
import com.green.keyboard_project.service.AdminProductService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import com.green.keyboard_project.service.ProductImageStorageService;
import com.green.keyboard_project.dto.ProductResponse;

@RestController
@RequestMapping("/api/admin/products")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
@AllArgsConstructor
public class AdminProductController {
    private final AdminProductService adminProductService;
    private final ProductImageStorageService imageStorageService;

    // 관리자 상품 전체 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts() {
        return ResponseEntity.ok(ApiResponse.ok(adminProductService.getAllProducts()));
    }

    // 관리자 상품 상세 조회
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable int productId) {
        return ResponseEntity.ok(ApiResponse.ok(adminProductService.getProduct(productId)));
    }

    // 상품 등록
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestPart("product") CreateProductRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        String imageUrl = image != null && !image.isEmpty() ? imageStorageService.store(image) : null;
        try {
            ProductResponse created = adminProductService.createProduct(request, imageUrl);
            URI location = URI.create("/api/admin/products/" + created.productId());

            return ResponseEntity.created(location).body(ApiResponse.ok(created));
        } catch (RuntimeException e) {
            imageStorageService.deleteByUrl(imageUrl);
            throw e;
        }
    }

    // 관리자 상품 수정
    @PatchMapping(value = "/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable int productId,
            @Valid @RequestPart("product") UpdateProductRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        String newImageUrl = image != null && !image.isEmpty() ? imageStorageService.store(image) : null;
        ProductResponse updated;
        try {
            updated = adminProductService.updateProduct(productId, request, newImageUrl);
        } catch (RuntimeException e) {
            imageStorageService.deleteByUrl(newImageUrl);
            throw e;
        }
        // 주문내역에서 사용할 수 있도록 교체 전 이미지 파일도 보존합니다.
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    // 상품을 논리 삭제하며 주문내역에 필요한 이미지 파일은 보존합니다.
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable int productId) {
        adminProductService.deleteProduct(productId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
