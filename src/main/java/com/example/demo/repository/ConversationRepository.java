package com.example.demo.repository;

import com.example.demo.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** JOIN FETCH user —— 避免事务结束后访问懒加载 user 抛 LazyInitializationException */
    @Query("select c from Conversation c join fetch c.user where c.id = :id")
    Optional<Conversation> findByIdWithUser(@Param("id") Long id);
}
