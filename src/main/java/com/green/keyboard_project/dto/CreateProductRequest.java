package com.green.keyboard_project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateProductRequest {
    @NotBlank
    @Size(max = 50)
    private String categoryId;

    @NotBlank
    @Size(max = 200)
    private String name;

    @NotNull
    @Positive
    private Integer basePrice;

    @NotNull
    @PositiveOrZero
    private Integer stockQuantity;

    @Size(max = 500)
    private String thumbnailUrl;

    private String description;

    @Size(max = 20)
    private String layout;

    @Size(max = 50)
    private String housingMaterial;

    @Size(max = 20)
    private String supportPins;

    private Boolean isWireless;

    @Positive
    private Integer switchPin;

    @Size(max = 50)
    private String switchType;

    @Size(max = 50)
    private String keycapMaterial;
}
