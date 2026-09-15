package com.example.demo.repository;

import com.example.demo.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    /** 只加载最近 20 条（时间倒序），Service 层再反转为正序 */
    List<Message> findTop20ByConversationIdOrderByCreatedAtDesc(Long conversationId);

    /** 幂等检查：同一会话同一 requestId 是否已存在 USER 消息 */
    boolean existsByConversationIdAndRequestId(Long conversationId, String requestId);
}
