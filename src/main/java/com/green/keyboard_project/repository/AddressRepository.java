package com.green.keyboard_project.repository;

import com.green.keyboard_project.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Integer> {
    Optional<Address> findByUser_UserIdAndIsDefaultTrue(int userId);
}
