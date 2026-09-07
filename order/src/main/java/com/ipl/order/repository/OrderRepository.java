package com.ipl.order.repository;
import com.ipl.order.domain.ClientOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderRepository extends JpaRepository<ClientOrder, Long> {
    List<ClientOrder> findByClientId(Long clientId);
}