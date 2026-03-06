package com.sasikala.SaveTheHuman.repository;

import com.sasikala.SaveTheHuman.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
}
