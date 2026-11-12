package com.green.keyboard_project.repository;

import com.green.keyboard_project.entity.ManualBuild;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ManualBuildRepository extends JpaRepository<ManualBuild, Integer> {
}
