package com.example.demo.repository;

import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户数据访问层
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** 按用户名查找（登录用） */
    Optional<User> findByUsername(String username);

    /** 用户名是否已存在（注册时查重） */
    boolean existsByUsername(String username);
}