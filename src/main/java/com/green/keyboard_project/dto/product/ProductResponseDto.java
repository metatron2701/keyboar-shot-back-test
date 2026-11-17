package com.green.keyboard_project.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponseDto {
    private String id;
    private String name;
    private String slug;
    private int basePrice;
    private String category; // 'housing' | 'pcb' | 'switches' | 'keycaps'
    private String partType; // 'HOUSING' | 'PCB' | 'SWITCH' | 'KEYCAP' (동적 계산)
    private String categoryId; // 'housing-alu' 등
    private String parentCategoryId; // 'housing' 등
    private String subCategoryName;
    private String description;
    private int stockQuantity;
    private int shippingFee;
    private String brand;
    @JsonProperty("isSoldOut")
    private boolean isSoldOut;
    private String status; // 'ACTIVE' | 'SOLD_OUT'

    // 키보드 스펙 필드
    private String layout;
    private String material;
    private String switchType;
    private Boolean isWireless;
    private Integer pin;

    @Builder.Default
    private List<Integer> supportPins = new ArrayList<>();

    @Builder.Default
    private List<MediaDto> images = new ArrayList<>();

    @Builder.Default
    private List<String> galleryImages = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MediaDto {
        private String id;
        private String url;
        private String alt;
    }
}
