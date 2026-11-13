package com.sis.entity;

import javax.persistence.*;

@Entity
@Table(name = "clubs")
public class Club {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "meeting_info", length = 255)
    private String meetingInfo;

    public Club() {}
    public Club(String slug, String name, String description, String meetingInfo) {
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.meetingInfo = meetingInfo;
    }
    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getMeetingInfo() { return meetingInfo; }
}
