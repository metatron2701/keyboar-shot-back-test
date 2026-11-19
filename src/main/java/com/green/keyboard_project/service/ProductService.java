package com.green.keyboard_project.service;

import com.green.keyboard_project.dto.product.CreateProductRequest;
import com.green.keyboard_project.dto.product.ProductResponse;
import com.green.keyboard_project.dto.product.ProductListResponseDto;
import com.green.keyboard_project.dto.product.ProductResponseDto;
import com.green.keyboard_project.entity.Category;
import com.green.keyboard_project.entity.Product;
import com.green.keyboard_project.entity.ProductImage;
import com.green.keyboard_project.entity.ProductSpec;
import com.green.keyboard_project.exceptions.ResourceNotFoundException;
import com.green.keyboard_project.repository.category.CategoryRepository;
import com.green.keyboard_project.repository.product.ProductImageRepository;
import com.green.keyboard_project.repository.product.ProductRepository;
import com.green.keyboard_project.repository.product.ProductSpecRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductSpecRepository productSpecRepository;
    private final ProductImageRepository productImageRepository;

    // ==================== [스토어프론트 조회 API - feature/products] ====================

    public ProductListResponseDto getProducts(String category, String subcategory, int page, int size) {
        // category 호환 처리 (keyboards -> pcb)
        if ("keyboards".equalsIgnoreCase(category)) {
            category = "pcb";
        }
        // subcategory 호환 처리 (housing-abs -> housing-plastic)
        if ("housing-abs".equalsIgnoreCase(subcategory)) {
            subcategory = "housing-plastic";
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.ASC, "productId"));
        Page<Product> productPage = productRepository.findFilteredProducts(
                (category != null && !category.isBlank()) ? category.trim() : null,
                (subcategory != null && !subcategory.isBlank()) ? subcategory.trim() : null,
                pageable
        );

        List<Product> products = productPage.getContent();
        List<Integer> productIds = products.stream().map(Product::getProductId).collect(Collectors.toList());
        Map<Integer, List<ProductImage>> imagesByProductId = productIds.isEmpty()
                ? Collections.emptyMap()
                : productImageRepository.findByProduct_ProductIdInOrderByDisplayOrderAsc(productIds)
                        .stream()
                        .collect(Collectors.groupingBy(img -> img.getProduct().getProductId()));

        List<ProductResponseDto> content = products.stream()
                .map(p -> toDto(p, imagesByProductId.getOrDefault(p.getProductId(), Collections.emptyList())))
                .collect(Collectors.toList());

        return ProductListResponseDto.builder()
                .content(content)
                .pageNumber(productPage.getNumber())
                .pageSize(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .build();
    }

    public ProductResponseDto getProductById(int productId) {
        Product product = productRepository.findById(productId)
                .filter(p -> !p.isDeleted() && p.isActive() && p.getStockQuantity() > 0)
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다. (ID: " + productId + ")"));
        List<ProductImage> galleryEntities = productImageRepository.findByProduct_ProductIdOrderByDisplayOrderAsc(productId);
        return toDto(product, galleryEntities);
    }

    private ProductResponseDto toDto(Product p, List<ProductImage> galleryEntities) {
        ProductSpec spec = productSpecRepository.findById(p.getProductId()).orElse(null);

        String parentId = "";
        if (p.getCategory() != null) {
            if (p.getCategory().getParent() != null) {
                parentId = p.getCategory().getParent().getCategoryId();
            } else {
                parentId = p.getCategory().getCategoryId();
            }
        }

        String normCategory = "pcb";
        if (parentId.contains("hous")) normCategory = "housing";
        else if (parentId.contains("switch")) normCategory = "switches";
        else if (parentId.contains("keycap")) normCategory = "keycaps";

        boolean isSoldOut = !p.isActive() || p.getStockQuantity() <= 0;
        String thumb = p.getThumbnailUrl() != null ? p.getThumbnailUrl() : "";

        List<ProductResponseDto.MediaDto> images = new ArrayList<>();
        List<String> gallery = new ArrayList<>();

        if (galleryEntities != null && !galleryEntities.isEmpty()) {
            for (int i = 0; i < galleryEntities.size(); i++) {
                String url = galleryEntities.get(i).getImageUrl();
                images.add(ProductResponseDto.MediaDto.builder()
                        .id(p.getProductId() + "-" + i)
                        .url(url)
                        .alt(p.getName() + " 이미지 " + (i + 1))
                        .build());
                gallery.add(url);
            }
        } else if (!thumb.isBlank()) {
            images.add(ProductResponseDto.MediaDto.builder()
                    .id(p.getProductId() + "-0")
                    .url(thumb)
                    .alt(p.getName())
                    .build());
            gallery.add(thumb);
        }

        String layout = null;
        String material = null;
        String switchType = null;
        Boolean isWireless = null;
        Integer pin = null;
        List<Integer> supportPins = Collections.emptyList();

        if (spec != null) {
            layout = spec.getLayout();
            if (spec.getHousingMaterial() != null) material = spec.getHousingMaterial();
            else if (spec.getKeycapMaterial() != null) material = spec.getKeycapMaterial();
            switchType = spec.getSwitchType();
            isWireless = spec.getIsWireless();
            pin = spec.getSwitchPin();
            supportPins = parseSupportPins(spec.getSupportPins());
        }

        return ProductResponseDto.builder()
                .id(String.valueOf(p.getProductId()))
                .name(p.getName())
                .slug(String.valueOf(p.getProductId()))
                .basePrice(p.getBasePrice())
                .category(normCategory)
                .partType(p.getDerivedPartType())
                .categoryId(p.getCategory() != null ? p.getCategory().getCategoryId() : null)
                .parentCategoryId(parentId)
                .subCategoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .description(p.getDescription())
                .stockQuantity(p.getStockQuantity())
                .shippingFee(0)
                .brand("MONO / STUDIO")
                .isSoldOut(isSoldOut)
                .status(isSoldOut ? "SOLD_OUT" : "ACTIVE")
                .layout(layout)
                .material(material)
                .switchType(switchType)
                .isWireless(isWireless)
                .pin(pin)
                .supportPins(supportPins)
                .images(images)
                .galleryImages(gallery)
                .build();
    }

    private List<Integer> parseSupportPins(String rawPins) {
        if (rawPins == null || rawPins.isBlank()) {
            return Collections.emptyList();
        }
        List<Integer> pins = new ArrayList<>();
        if (rawPins.contains("3")) pins.add(3);
        if (rawPins.contains("5")) pins.add(5);
        return pins;
    }

    // ==================== [관리자 CRUD - develop] ====================

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request, String imageUrl) {
        Category category = findRootCategory(request.getCategoryName());
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

    @Transactional
    public ProductResponse updateProduct(int productId, CreateProductRequest request, String newImageUrl) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다."));
        Category category = findRootCategory(request.getCategoryName());
        product.setCategory(category);
        product.setName(request.getName());
        product.setBasePrice(request.getBasePrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setDescription(request.getDescription());
        if (newImageUrl != null) product.setThumbnailUrl(newImageUrl);
        product.setUpdatedAt(LocalDateTime.now());
        ProductSpec spec = productSpecRepository.findById(productId).orElseGet(() -> {
            ProductSpec created = new ProductSpec();
            created.setProduct(product);
            return created;
        });
        spec.setLayout(request.getLayout());
        spec.setHousingMaterial(request.getHousingMaterial());
        spec.setSupportPins(request.getSupportPins());
        spec.setIsWireless(request.getIsWireless());
        spec.setSwitchPin(request.getSwitchPin());
        spec.setSwitchType(request.getSwitchType());
        spec.setKeycapMaterial(request.getKeycapMaterial());
        productSpecRepository.save(spec);
        Product saved = productRepository.save(product);
        return ProductResponse.from(saved, spec);
    }

    @Transactional(readOnly = true)
    public String getThumbnailUrl(int productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다."))
                .getThumbnailUrl();
    }

    @Transactional
    public void deleteProduct(int productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다."));
        product.setActive(false);
        product.setUpdatedAt(LocalDateTime.now());
    }

    private Category findRootCategory(String categoryName) {
        String categoryId = switch (categoryName.trim()) {
            case "하우징" -> "housing";
            case "기판", "기판 (PCB)" -> "pcb";
            case "스위치" -> "switches";
            case "키캡" -> "keycaps";
            default -> throw new ResourceNotFoundException("카테고리를 찾을 수 없습니다.");
        };

        return categoryRepository.findById(categoryId)
                .filter(category -> category.getParent() == null)
                .orElseThrow(() -> new ResourceNotFoundException("카테고리를 찾을 수 없습니다."));
    }
}
