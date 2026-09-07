package com.ipl.product.repository;

import com.ipl.product.domain.ProcessedOrder;
import org.springframework.data.jpa.repository.JpaRepository;

// +++ FICHIER ENTIEREMENT NOUVEAU
public interface ProcessedOrderRepository extends JpaRepository<ProcessedOrder, Long> {}
