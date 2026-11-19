package com.sis.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "library_items", uniqueConstraints = @UniqueConstraint(name = "uk_library_isbn", columnNames = "isbn"))
public class LibraryItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 30) private String isbn;
    @Column(nullable = false, length = 255) private String title;
    @Column(nullable = false, length = 200) private String author;
    @Column(name = "total_copies", nullable = false) private Integer totalCopies;
    @Column(name = "available_copies", nullable = false) private Integer availableCopies;
    @Column(nullable = false) private Boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public Integer getTotalCopies() { return totalCopies; }
    public void setTotalCopies(Integer totalCopies) { this.totalCopies = totalCopies; }
    public Integer getAvailableCopies() { return availableCopies; }
    public void setAvailableCopies(Integer availableCopies) { this.availableCopies = availableCopies; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}