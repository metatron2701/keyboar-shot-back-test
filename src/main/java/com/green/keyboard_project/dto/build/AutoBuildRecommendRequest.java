package com.green.keyboard_project.dto.build;

import jakarta.validation.constraints.NotBlank;
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
public class AutoBuildRecommendRequest {

    @NotBlank(message = "배열 설문 값은 필수입니다.")
    @Size(max = 20, message = "배열 설문 값은 20자 이하여야 합니다.")
    private String surveyLayout;

    @Size(max = 20, message = "연결 방식 설문 값은 20자 이하여야 합니다.")
    private String surveyConnectivity;

    @Size(max = 20, message = "타건음 설문 값은 20자 이하여야 합니다.")
    private String surveySound;

    @Size(max = 20, message = "키캡 설문 값은 20자 이하여야 합니다.")
    private String surveyKeycap;

    private Boolean includeAssembly;
}
