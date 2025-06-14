package com.example.cloud_box.repository;

import com.example.cloud_box.model.User;
import lombok.Data;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByUsername(String username);
}
