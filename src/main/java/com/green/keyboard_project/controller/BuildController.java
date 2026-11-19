package com.green.keyboard_project.controller;

import com.green.keyboard_project.dto.ApiResponse;
import com.green.keyboard_project.dto.build.AutoBuildRecommendRequest;
import com.green.keyboard_project.dto.build.AutoBuildRequest;
import com.green.keyboard_project.dto.build.BuildResponse;
import com.green.keyboard_project.dto.build.ManualBuildRequest;
import com.green.keyboard_project.service.BuildService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/builds")
@RequiredArgsConstructor
public class BuildController {

    private final BuildService buildService;

    @PostMapping("/manual")
    public ResponseEntity<ApiResponse<BuildResponse>> createManualBuild(
            @AuthenticationPrincipal Integer userId,
            @Valid @RequestBody ManualBuildRequest request
    ) {
        BuildResponse response = buildService.createManualBuild(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/manual/{buildId}")
    public ResponseEntity<ApiResponse<BuildResponse>> getManualBuild(
            @AuthenticationPrincipal Integer userId,
            @PathVariable("buildId") int buildId
    ) {
        BuildResponse response = buildService.getManualBuild(userId, buildId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/manual/{buildId}")
    public ResponseEntity<ApiResponse<BuildResponse>> updateManualBuild(
            @AuthenticationPrincipal Integer userId,
            @PathVariable("buildId") int buildId,
            @Valid @RequestBody ManualBuildRequest request
    ) {
        BuildResponse response = buildService.updateManualBuild(userId, buildId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/auto/recommend")
    public ResponseEntity<ApiResponse<BuildResponse>> recommendAutoBuild(
            @Valid @RequestBody AutoBuildRecommendRequest request
    ) {
        BuildResponse response = buildService.recommendAutoBuild(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/auto")
    public ResponseEntity<ApiResponse<BuildResponse>> createAutoBuild(
            @AuthenticationPrincipal Integer userId,
            @Valid @RequestBody AutoBuildRequest request
    ) {
        BuildResponse response = buildService.createAutoBuild(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/auto/{buildId}")
    public ResponseEntity<ApiResponse<BuildResponse>> getAutoBuild(
            @AuthenticationPrincipal Integer userId,
            @PathVariable("buildId") int buildId
    ) {
        BuildResponse response = buildService.getAutoBuild(userId, buildId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/auto/{buildId}")
    public ResponseEntity<ApiResponse<BuildResponse>> updateAutoBuild(
            @AuthenticationPrincipal Integer userId,
            @PathVariable("buildId") int buildId,
            @Valid @RequestBody AutoBuildRequest request
    ) {
        BuildResponse response = buildService.updateAutoBuild(userId, buildId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
