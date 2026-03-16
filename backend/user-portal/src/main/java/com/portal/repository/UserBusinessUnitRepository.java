package com.portal.repository;

import com.platform.security.entity.UserBusinessUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserBusinessUnitRepository extends JpaRepository<UserBusinessUnit, String> {

    List<UserBusinessUnit> findByUserId(String userId);

    List<UserBusinessUnit> findByBusinessUnitIdIn(List<String> businessUnitIds);
}
