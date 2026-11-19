package com.green.keyboard_project.repository;

import com.green.keyboard_project.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {
    List<Category> findByParentIsNullOrderByDisplayOrderAsc();
    List<Category> findByParent_CategoryIdOrderByDisplayOrderAsc(String parentId);
}
