package com.green.keyboard_project.repository;

import com.green.keyboard_project.entity.ManualBuildItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ManualBuildItemRepository extends JpaRepository<ManualBuildItem, Integer> {
    List<ManualBuildItem> findByManualBuild_ManualBuildIdOrderByManualItemIdAsc(int manualBuildId);

    void deleteByManualBuild_ManualBuildId(int manualBuildId);
}
