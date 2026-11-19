package com.green.keyboard_project.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateProductRequest {
    @Size(min = 1, max = 50)
    private String categoryId;

    @Pattern(regexp = ".*\\S.*", message = "상품명은 공백일 수 없습니다.")
    @Size(max = 200)
    private String name;

    @Positive
    private Integer basePrice;

    @PositiveOrZero
    private Integer stockQuantity;

    private Boolean active;

    private String description;

    @Pattern(regexp = ".*\\S.*", message = "배열 규격은 공백일 수 없습니다.")
    @Size(max = 20)
    private String layout;

    @Pattern(regexp = ".*\\S.*", message = "하우징 소재는 공백일 수 없습니다.")
    @Size(max = 50)
    private String housingMaterial;

    @Pattern(regexp = ".*\\S.*", message = "지원 핀 규격은 공백일 수 없습니다.")
    @Size(max = 20)
    private String supportPins;

    private Boolean isWireless;

    @Positive
    private Integer switchPin;

    @Pattern(regexp = ".*\\S.*", message = "스위치 종류는 공백일 수 없습니다.")
    @Size(max = 50)
    private String switchType;

    @Pattern(regexp = ".*\\S.*", message = "키캡 소재는 공백일 수 없습니다.")
    @Size(max = 50)
    private String keycapMaterial;
}
