package com.green.keyboard_project.dto.product;

import com.green.keyboard_project.entity.Product;
import com.green.keyboard_project.entity.ProductSpec;

public record ProductResponse(
        int productId,
        String categoryName,
        String categoryId,
        String name,
        int basePrice,
        int stockQuantity,
        String thumbnailUrl,
        String description,
        boolean active,
        String layout,
        String housingMaterial,
        String supportPins,
        Boolean isWireless,
        Integer switchPin,
        String switchType,
        String keycapMaterial
) {
    public static ProductResponse from(Product product, ProductSpec spec) {
        return new ProductResponse(
                product.getProductId(),
                product.getCategory().getName(),
                product.getCategory().getCategoryId(),
                product.getName(),
                product.getBasePrice(),
                product.getStockQuantity(),
                product.getThumbnailUrl(),
                product.getDescription(),
                product.isActive(),
                spec == null ? null : spec.getLayout(),
                spec == null ? null : spec.getHousingMaterial(),
                spec == null ? null : spec.getSupportPins(),
                spec == null ? null : spec.getIsWireless(),
                spec == null ? null : spec.getSwitchPin(),
                spec == null ? null : spec.getSwitchType(),
                spec == null ? null : spec.getKeycapMaterial());
    }
}
