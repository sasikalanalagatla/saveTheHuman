package com.sasikala.SaveTheHuman.repository;

import com.sasikala.SaveTheHuman.entity.User;
import com.sasikala.SaveTheHuman.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByUserAndLevel(User user, Integer level);
    Optional<Question> findByUserAndLevelAndCompleted(User user, Integer level, boolean completed);
}
