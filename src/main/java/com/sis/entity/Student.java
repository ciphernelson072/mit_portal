package com.sis.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.persistence.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "students")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(nullable = false)
    private String fullName;

    @Column
    private String className;

    // Cumulative GPA calculated from all grades
    @Column(precision = 4, scale = 2, columnDefinition = "DECIMAL(4,2) DEFAULT 0.00")
    private BigDecimal cgpa = BigDecimal.ZERO;

    // Profile image file path for student photos
    @Column
    private String profileImagePath;

    // Email address for direct communication with student
    @Column
    private String email;

    // Phone number for contact purposes
    @Column
    private String phone;

    // Enrollment status - when the student was admitted
    @Column
    private String enrollmentDate;

    // Academic information - address and other details
    @Column(length = 500)
    private String address;

    @ManyToMany(mappedBy = "students")
    @JsonIgnore
    private Set<Course> courses = new HashSet<>();

    public Student() {
    }

    public Student(User user, String fullName, String className) {
        this.user = user;
        this.fullName = fullName;
        this.className = className;
        this.cgpa = BigDecimal.ZERO;
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

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Set<Course> getCourses() {
        return courses;
    }

    public void setCourses(Set<Course> courses) {
        this.courses = courses;
    }

    /**
     * Get student's Cumulative GPA
     * @return CGPA as BigDecimal with 2 decimal places
     */
    public BigDecimal getCgpa() {
        return cgpa;
    }

    /**
     * Set student's Cumulative GPA
     * @param cgpa calculated CGPA
     */
    public void setCgpa(BigDecimal cgpa) {
        this.cgpa = cgpa;
    }

    /**
     * Get path to student's profile image
     * @return image file path
     */
    public String getProfileImagePath() {
        return profileImagePath;
    }

    /**
     * Set path to student's profile image
     * @param profileImagePath path to image file
     */
    public void setProfileImagePath(String profileImagePath) {
        this.profileImagePath = profileImagePath;
    }

    /**
     * Get student's email address
     * @return email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Set student's email address
     * @param email student's email
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Get student's phone number
     * @return phone number
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Set student's phone number
     * @param phone student's phone number
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * Get student's enrollment date
     * @return enrollment date
     */
    public String getEnrollmentDate() {
        return enrollmentDate;
    }

    /**
     * Set student's enrollment date
     * @param enrollmentDate date of admission/enrollment
     */
    public void setEnrollmentDate(String enrollmentDate) {
        this.enrollmentDate = enrollmentDate;
    }

    /**
     * Get student's address
     * @return residential address
     */
    public String getAddress() {
        return address;
    }

    /**
     * Set student's address
     * @param address residential address
     */
    public void setAddress(String address) {
        this.address = address;
    }
}
