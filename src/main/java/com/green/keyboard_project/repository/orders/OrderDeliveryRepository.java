package com.green.keyboard_project.repository.orders;

import com.green.keyboard_project.entity.OrderDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderDeliveryRepository extends JpaRepository<OrderDelivery, Integer> {
}
