package com.green.keyboard_project.dto.build;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuildResponse {

    private Integer buildId;
    private String buildType; // "MANUAL" | "AUTO"
    private Integer userId;
    private String buildName;
    private boolean includeAssembly;
    private int assemblyFee;
    private int totalPrice;
    private boolean copiedOnWrite;
    private LocalDateTime createdAt;

    // 자동 빌드 설문 정보 (MANUAL일 때는 null)
    private String surveyLayout;
    private String surveyConnectivity;
    private String surveySound;
    private String surveyKeycap;

    private List<BuildItemDto> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BuildItemDto {
        private String partType; // "HOUSING" | "PCB" | "SWITCH" | "KEYCAP"
        private int productId;
        private String productName;
        private int unitPrice;
        private String thumbnailUrl;
        private String layout;
        private List<Integer> supportPins;
        private Integer switchPin;
        private String switchType;
        private Boolean isWireless;
        private String material;
    }
}
