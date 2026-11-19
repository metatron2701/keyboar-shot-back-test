package com.green.keyboard_project.repository;

import com.green.keyboard_project.entity.AutoBuildItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AutoBuildItemRepository extends JpaRepository<AutoBuildItem, Integer> {
    List<AutoBuildItem> findByAutoBuild_AutoBuildIdOrderByAutoItemIdAsc(int autoBuildId);

    void deleteByAutoBuild_AutoBuildId(int autoBuildId);
}
