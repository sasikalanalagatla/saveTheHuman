package com.sasikala.SaveTheHuman.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String word;

    @Column(nullable = false)
    private String hint;

    @Column(nullable = false)
    private Integer level;

    @Column(nullable = false)
    private boolean completed = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public Question() {}

    public Question(String word, String hint, Integer level, User user) {
        this.word = word.toUpperCase();
        this.hint = hint;
        this.level = level;
        this.user = user;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }

    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
