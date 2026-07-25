package com.economicbriefing.classifier.repository;

import java.util.List;
import java.util.Optional;

import com.economicbriefing.classifier.entity.TeacherLabelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherLabelRepository extends JpaRepository<TeacherLabelEntity, Long> {

    Optional<TeacherLabelEntity> findByArticleIdAndTeacherPromptVersion(
            String articleId, String teacherPromptVersion);

    List<TeacherLabelEntity> findAllByArticleId(String articleId);
}
