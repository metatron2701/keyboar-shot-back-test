package com.green.keyboard_project.repository;

import com.green.keyboard_project.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Integer> {

    List<Refund> findByPayment_PaymentIdOrderByRequestedAtDesc(int paymentId);
}
