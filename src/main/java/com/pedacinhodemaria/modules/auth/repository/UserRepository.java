package com.pedacinhodemaria.modules.auth.repository;

import com.pedacinhodemaria.modules.auth.domain.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}