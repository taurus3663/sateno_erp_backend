package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.DiscountPhone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiscountPhoneRepository extends JpaRepository<DiscountPhone, Long> {

    List<DiscountPhone> findByPhoneNumber(String phoneNumber);
}
