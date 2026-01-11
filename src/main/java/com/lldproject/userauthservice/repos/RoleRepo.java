package com.lldproject.userauthservice.repos;

import com.lldproject.userauthservice.models.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepo extends JpaRepository<Role,String> {
    Optional<Role> findByValue(String value);
}
