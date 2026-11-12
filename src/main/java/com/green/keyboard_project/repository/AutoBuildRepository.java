package com.green.keyboard_project.repository;

import com.green.keyboard_project.entity.AutoBuild;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AutoBuildRepository extends JpaRepository<AutoBuild, Integer> {
}
