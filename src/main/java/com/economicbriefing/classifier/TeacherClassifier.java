package com.economicbriefing.classifier;

import com.economicbriefing.domain.article.Article;

public interface TeacherClassifier {

    TeacherLabelResponse classify(Article article);
}
