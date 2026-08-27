package com.tabish.SpringBootLab.repository;

import com.tabish.SpringBootLab.model.Order;
import com.tabish.SpringBootLab.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    Optional<Payment> findByOrder(Order order);

    boolean existsByOrder(Order order);

    long countByOrder_Id(Integer orderId);
}