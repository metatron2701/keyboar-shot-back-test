package com.green.keyboard_project.service;

import com.green.keyboard_project.dto.category.CategoryResponse;
import com.green.keyboard_project.entity.Category;
import com.green.keyboard_project.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getAllCategories() {
        List<Category> parents = categoryRepository.findByParentIsNullOrderByDisplayOrderAsc();
        List<CategoryResponse> result = new ArrayList<>();

        // '전체 상품 (all)' 가상 카테고리 추가 (하위 항목: 대분류 4개)
        CategoryResponse allCategory = CategoryResponse.builder()
                .id("all")
                .label("전체 상품")
                .code("ALL PRODUCTS")
                .subcategories(parents.stream()
                        .map(p -> new CategoryResponse.SubCategoryDto(p.getCategoryId(), p.getName()))
                        .collect(Collectors.toList()))
                .build();
        result.add(allCategory);

        for (Category parent : parents) {
            List<Category> children = categoryRepository.findByParent_CategoryIdOrderByDisplayOrderAsc(parent.getCategoryId());
            List<CategoryResponse.SubCategoryDto> subList = children.stream()
                    .map(c -> new CategoryResponse.SubCategoryDto(c.getCategoryId(), c.getName()))
                    .collect(Collectors.toList());

            result.add(CategoryResponse.builder()
                    .id(parent.getCategoryId())
                    .label(parent.getName())
                    .code(parent.getCategoryId().toUpperCase())
                    .subcategories(subList)
                    .build());
        }

        return result;
    }
}
