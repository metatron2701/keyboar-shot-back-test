package com.green.keyboard_project.dto;

import com.green.keyboard_project.entity.Category;
import com.green.keyboard_project.entity.Product;
import com.green.keyboard_project.entity.ProductSpec;

import java.time.LocalDateTime;

public record ProductResponse(
        int productId,
        String categoryName,
        String categoryId,
        String parentCategoryId,
        String name,
        int basePrice,
        int stockQuantity,
        String thumbnailUrl,
        String description,
        boolean active,
        LocalDateTime createdAt,
        String layout,
        String housingMaterial,
        String supportPins,
        Boolean isWireless,
        Integer switchPin,
        String switchType,
        String keycapMaterial
) {
    public static ProductResponse from(Product product, ProductSpec spec) {
        Category category = product.getCategory();
        Category parent = category.getParent();
        return new ProductResponse(
                product.getProductId(),
                category.getName(),
                category.getCategoryId(),
                parent == null ? category.getCategoryId() : parent.getCategoryId(),
                product.getName(),
                product.getBasePrice(),
                product.getStockQuantity(),
                product.getThumbnailUrl(),
                product.getDescription(),
                product.isActive(),
                product.getCreatedAt(),
                spec == null ? null : spec.getLayout(),
                spec == null ? null : spec.getHousingMaterial(),
                spec == null ? null : spec.getSupportPins(),
                spec == null ? null : spec.getIsWireless(),
                spec == null ? null : spec.getSwitchPin(),
                spec == null ? null : spec.getSwitchType(),
                spec == null ? null : spec.getKeycapMaterial());
    }
}
