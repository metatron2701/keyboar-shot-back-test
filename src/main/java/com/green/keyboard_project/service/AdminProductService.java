package com.green.keyboard_project.service;

import com.green.keyboard_project.dto.CreateProductRequest;
import com.green.keyboard_project.dto.ProductResponse;
import com.green.keyboard_project.dto.UpdateProductRequest;
import com.green.keyboard_project.entity.Category;
import com.green.keyboard_project.entity.Product;
import com.green.keyboard_project.entity.ProductSpec;
import com.green.keyboard_project.exceptions.ResourceNotFoundException;
import com.green.keyboard_project.repository.CategoryRepository;
import com.green.keyboard_project.repository.ProductRepository;
import com.green.keyboard_project.repository.ProductSpecRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductSpecRepository productSpecRepository;

    // 관리자 상품 등록
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request, String imageUrl) {
        Category category = findSubcategory(request.getCategoryId());
        LocalDateTime now = LocalDateTime.now();

        Product product = new Product();
        product.setCategory(category);
        product.setName(request.getName());
        product.setBasePrice(request.getBasePrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setThumbnailUrl(imageUrl);
        product.setDescription(request.getDescription());
        product.setActive(true);
        product.setVersion(0);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        Product savedProduct = productRepository.save(product);

        ProductSpec productSpec = new ProductSpec();
        productSpec.setProduct(savedProduct);
        productSpec.setLayout(request.getLayout());
        productSpec.setHousingMaterial(request.getHousingMaterial());
        productSpec.setSupportPins(request.getSupportPins());
        productSpec.setIsWireless(request.getIsWireless());
        productSpec.setSwitchPin(request.getSwitchPin());
        productSpec.setSwitchType(request.getSwitchType());
        productSpec.setKeycapMaterial(request.getKeycapMaterial());

        productSpecRepository.save(productSpec);

        return ProductResponse.from(savedProduct, productSpec);
    }

    // 관리자 전체 상품 조회
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .filter(product -> !product.isDeleted())
                .map(product -> ProductResponse.from(
                        product,
                        productSpecRepository.findById(product.getProductId()).orElse(null)))
                .toList();
    }

    // 관리자 상세 상품 조회
    @Transactional(readOnly = true)
    public ProductResponse getProduct(int productId) {
        Product product = productRepository.findById(productId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"));
        ProductSpec spec = productSpecRepository.findById(productId).orElse(null);
        return ProductResponse.from(product, spec);
    }

    // 관리자 상품 삭제 (soft delete 적용)
    @Transactional
    public void deleteProduct(int productId) {
        Product product = productRepository.findById(productId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"));

        LocalDateTime now = LocalDateTime.now();
        product.setActive(false);
        product.setDeleted(true);
        product.setDeletedAt(now);
        product.setUpdatedAt(now);
    }

    // 관리자 상품 수정
    // 전달된 항목만 수정하고, 요청에서 빠진 항목은 기존 값을 유지
    @Transactional
    public ProductResponse updateProduct(int productId, UpdateProductRequest request, String imageUrl) {
        Product product = productRepository.findById(productId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"));

        String previousParentId = parentCategoryId(product.getCategory());
        if (request.getCategoryId() != null) {
            product.setCategory(findSubcategory(request.getCategoryId()));
        }
        String parentId = parentCategoryId(product.getCategory());

        if (request.getName() != null) product.setName(request.getName());
        if (request.getBasePrice() != null) product.setBasePrice(request.getBasePrice());
        if (request.getStockQuantity() != null) product.setStockQuantity(request.getStockQuantity());
        if (request.getActive() != null) product.setActive(request.getActive());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (imageUrl != null) product.setThumbnailUrl(imageUrl);
        product.setUpdatedAt(LocalDateTime.now());

        ProductSpec spec = productSpecRepository.findById(productId).orElse(null);
        if (spec == null) {
            spec = new ProductSpec();
            spec.setProduct(product);
        }
        if (request.getLayout() != null) spec.setLayout(request.getLayout());
        if (request.getHousingMaterial() != null) spec.setHousingMaterial(request.getHousingMaterial());
        if (request.getSupportPins() != null) spec.setSupportPins(request.getSupportPins());
        if (request.getIsWireless() != null) spec.setIsWireless(request.getIsWireless());
        if (request.getSwitchPin() != null) spec.setSwitchPin(request.getSwitchPin());
        if (request.getSwitchType() != null) spec.setSwitchType(request.getSwitchType());
        if (request.getKeycapMaterial() != null) spec.setKeycapMaterial(request.getKeycapMaterial());

        if (!parentId.equals(previousParentId)) {
            clearUnusedSpecs(spec, parentId);
        }

        ProductSpec savedSpec = productSpecRepository.save(spec);
        return ProductResponse.from(product, savedSpec);
    }

    // 이미지 교체 전에 기존 URL을 조회해 수정 성공 후 기존 파일을 정리합니다.
    @Transactional(readOnly = true)
    public String getThumbnailUrl(int productId) {
        return productRepository.findById(productId)
                .filter(product -> !product.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다"))
                .getThumbnailUrl();
    }

    // 카테고리 보조 메서드
    private Category findSubcategory(String categoryId) {
        return categoryRepository.findById(categoryId)
                .filter(category -> category.getParent() != null && category.getParent().getParent() == null)
                .orElseThrow(() -> new ResourceNotFoundException("소분류 카테고리를 찾을 수 없습니다."));
    }

    private String parentCategoryId(Category category) {
        Category parent = category.getParent();
        return parent == null ? category.getCategoryId() : parent.getCategoryId();
    }

    private void clearUnusedSpecs(ProductSpec spec, String parentId) {
        if (!"housing".equals(parentId) && !"pcb".equals(parentId)) {
            spec.setLayout(null);
        }
        if (!"housing".equals(parentId)) {
            spec.setHousingMaterial(null);
        }
        if (!"pcb".equals(parentId)) {
            spec.setSupportPins(null);
            spec.setIsWireless(null);
        }
        if (!"switches".equals(parentId)) {
            spec.setSwitchPin(null);
            spec.setSwitchType(null);
        }
        if (!"keycaps".equals(parentId)) {
            spec.setKeycapMaterial(null);
        }
    }
}
