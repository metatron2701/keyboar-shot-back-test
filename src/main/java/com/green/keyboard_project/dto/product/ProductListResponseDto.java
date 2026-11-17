package com.green.keyboard_project.dto.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductListResponseDto {
    private List<ProductResponseDto> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
}
