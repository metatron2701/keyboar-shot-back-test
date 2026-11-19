package com.green.keyboard_project.repository;

import com.green.keyboard_project.entity.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewImageRepository extends JpaRepository<ReviewImage, Integer> {

    List<ReviewImage> findByReview_ReviewIdOrderByDisplayOrderAsc(int reviewId);

    List<ReviewImage> findByReview_ReviewIdInOrderByDisplayOrderAsc(List<Integer> reviewIds);
}
