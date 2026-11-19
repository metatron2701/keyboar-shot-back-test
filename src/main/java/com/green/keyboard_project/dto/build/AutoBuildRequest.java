package com.green.keyboard_project.dto.build;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoBuildRequest {

    @Size(max = 100, message = "빌드명은 100자 이하여야 합니다.")
    private String buildName;

    @NotBlank(message = "배열 설문 값은 필수입니다.")
    @Size(max = 20, message = "배열 설문 값은 20자 이하여야 합니다.")
    private String surveyLayout;

    @NotBlank(message = "연결 방식 설문 값은 필수입니다.")
    @Size(max = 20, message = "연결 방식 설문 값은 20자 이하여야 합니다.")
    private String surveyConnectivity;

    @NotBlank(message = "타건음 설문 값은 필수입니다.")
    @Size(max = 20, message = "타건음 설문 값은 20자 이하여야 합니다.")
    private String surveySound;

    @NotBlank(message = "키캡 설문 값은 필수입니다.")
    @Size(max = 20, message = "키캡 설문 값은 20자 이하여야 합니다.")
    private String surveyKeycap;

    @NotNull(message = "하우징 상품 ID는 필수입니다.")
    private Integer housingProductId;

    @NotNull(message = "기판 상품 ID는 필수입니다.")
    private Integer pcbProductId;

    @NotNull(message = "스위치 상품 ID는 필수입니다.")
    private Integer switchProductId;

    @NotNull(message = "키캡 상품 ID는 필수입니다.")
    private Integer keycapProductId;

    private Boolean includeAssembly;
}
