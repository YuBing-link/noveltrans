package com.yumu.noveltranslator.port.out;

import com.yumu.noveltranslator.adapter.out.persistence.entity.User;

import java.util.Optional;

public interface UserRepositoryPort {
    Optional<User> findByEmail(String email);
    Optional<User> findById(Long id);
    void save(User user);
    void update(User user);
}
