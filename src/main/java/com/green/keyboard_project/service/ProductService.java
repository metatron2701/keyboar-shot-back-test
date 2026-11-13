package com.green.keyboard_project.service;

import com.green.keyboard_project.dto.ProductResponse;
import com.green.keyboard_project.entity.Product;
import com.green.keyboard_project.exceptions.InvalidStateException;
import com.green.keyboard_project.exceptions.ResourceNotFountException;
import com.green.keyboard_project.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository; // 상품 DB 접근 계층

    /**
     * 상품 전체 목록 페이징 및 정렬 조회
     *
     * @param page  요청 페이지 번호 (0-indexed)
     * @param size  한 페이지당 상품 수
     * @param order 정렬 기준 ("latest": 최신등록일순, "category": 카테고리 ID 역순)
     * @return DTO로 변환된 상품 Page 객체
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProduct(int page, int size, String order) {
        // 1. 정렬 기준(Sort) 설정
        Sort sort;
        if (order.equals("latest")) {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        } else if (order.equals("category")) {
            sort = Sort.by(Sort.Direction.DESC, "category.categoryId");
        } else {
            throw new InvalidStateException("잘못된 정렬 옵션입니다.");
        }

        // 2. 페이징 요청 객체 생성 및 활성 상태(isActive = true) 상품만 조회
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Product> products = productRepository.findByIsActiveTrue(pageable);

        // 3. 엔티티 목록을 클라이언트 응답 DTO 목록으로 변환
        List<ProductResponse> productResponses = new ArrayList<>();

        for (Product product : products) {
            productResponses.add(toProductResponse(product));
        }

        // 4. Page 구현체로 감싸서 총 개수 및 페이징 정보와 함께 반환
        return new PageImpl<>(productResponses, pageable, products.getTotalElements());
    }

    /**
     * 상품 단건 상세 조회 (상품 ID 기준)
     */
    @Transactional(readOnly = true)
    public ProductResponse getProductById(int productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFountException("상품을 찾을 수 없습니다. (ID: " + productId + ")"));
        return toProductResponse(product);
    }

    // 엔티티 -> DTO 변환 헬퍼 메서드
    private ProductResponse toProductResponse(Product product) {
        return new ProductResponse(
                product.getProductId(),
                product.getCategory() != null ? product.getCategory().getCategoryId() : null,
                product.getName(),
                product.getBasePrice(),
                product.getStockQuantity(),
                product.getThumbnailUrl(),
                product.getDescription(),
                product.isActive(),
                product.getVersion(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
