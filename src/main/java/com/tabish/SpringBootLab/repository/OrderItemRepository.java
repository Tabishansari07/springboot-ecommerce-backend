package com.tabish.SpringBootLab.repository;

import com.tabish.SpringBootLab.model.Order;
import com.tabish.SpringBootLab.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {

    List<OrderItem> findByOrder(Order order);
}