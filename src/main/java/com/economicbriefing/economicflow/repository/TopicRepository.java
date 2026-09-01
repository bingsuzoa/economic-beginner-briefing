package com.economicbriefing.economicflow.repository;

import java.util.List;
import java.util.Optional;
import com.economicbriefing.economicflow.entity.TopicEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<TopicEntity, Long> {
    Optional<TopicEntity> findByTopicKeyAndActiveTrue(String topicKey);
    List<TopicEntity> findByActiveTrue();
}
