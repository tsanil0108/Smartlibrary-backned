package com.smartlibrary.repository;

import com.smartlibrary.entity.Role;
import com.smartlibrary.entity.User;

import jakarta.persistence.LockModeType;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository
        extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(
            @Param("id") Long id
    );

    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.name) LIKE :q
            OR LOWER(u.email) LIKE :q
            """)
    Page<User> search(
            @Param("q") String q,
            Pageable pageable
    );
}