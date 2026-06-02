package com.sis.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "teachers")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(nullable = false)
    private String fullName;

    // Profile image file path for teacher photos
    @Column
    private String profileImagePath;

    // Email address for direct communication with teacher
    @Column
    private String email;

    // Phone number for contact purposes
    @Column
    private String phone;

    // Academic qualification/credentials
    @Column(length = 500)
    private String qualification;

    // Years of experience in teaching
    @Column
    private Integer yearsOfExperience;

    // Department or subject specialization
    @Column
    private String department;

    @OneToMany(mappedBy = "teacher")
    @JsonIgnore
    private Set<Course> courses = new HashSet<>();

    public Teacher() {
    }

    public Teacher(User user, String fullName) {
        this.user = user;
        this.fullName = fullName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Set<Course> getCourses() {
        return courses;
    }

    public void setCourses(Set<Course> courses) {
        this.courses = courses;
    }

    /**
     * Get path to teacher's profile image
     * @return image file path
     */
    public String getProfileImagePath() {
        return profileImagePath;
    }

    /**
     * Set path to teacher's profile image
     * @param profileImagePath path to image file
     */
    public void setProfileImagePath(String profileImagePath) {
        this.profileImagePath = profileImagePath;
    }

    /**
     * Get teacher's email address
     * @return email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Set teacher's email address
     * @param email teacher's email
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Get teacher's phone number
     * @return phone number
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Set teacher's phone number
     * @param phone teacher's phone number
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * Get teacher's academic qualification
     * @return qualification details
     */
    public String getQualification() {
        return qualification;
    }

    /**
     * Set teacher's academic qualification
     * @param qualification academic credentials
     */
    public void setQualification(String qualification) {
        this.qualification = qualification;
    }

    /**
     * Get teacher's years of experience
     * @return years of experience
     */
    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    /**
     * Set teacher's years of experience
     * @param yearsOfExperience years of teaching experience
     */
    public void setYearsOfExperience(Integer yearsOfExperience) {
        this.yearsOfExperience = yearsOfExperience;
    }

    /**
     * Get teacher's department or subject specialization
     * @return department name
     */
    public String getDepartment() {
        return department;
    }

    /**
     * Set teacher's department or subject specialization
     * @param department department name
     */
    public void setDepartment(String department) {
        this.department = department;
    }
}
