package com.green.keyboard_project.repository;

import com.green.keyboard_project.entity.ManualBuildItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ManualBuildItemRepository extends JpaRepository<ManualBuildItem, Integer> {
}
