package com.sis.dto;

public class CourseUnitDto {
    private Long id;
    private String unitCode;
    private String name;
    private Long courseId;
    private String courseName;
    private String courseCode;
    private String category;
    private Long teacherId;
    private String teacherName;

    public CourseUnitDto() {}

    public CourseUnitDto(Long id, String unitCode, String name, Long courseId, String courseName,
                         String courseCode, String category, Long teacherId, String teacherName) {
        this.id = id;
        this.unitCode = unitCode;
        this.name = name;
        this.courseId = courseId;
        this.courseName = courseName;
        this.courseCode = courseCode;
        this.category = category;
        this.teacherId = teacherId;
        this.teacherName = teacherName;
    }

    public Long getId() { return id; }
    public String getUnitCode() { return unitCode; }
    public String getName() { return name; }
    public Long getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public String getCourseCode() { return courseCode; }
    public String getCategory() { return category; }
    public Long getTeacherId() { return teacherId; }
    public String getTeacherName() { return teacherName; }
}