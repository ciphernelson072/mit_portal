package com.sis.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "hostel_rooms", uniqueConstraints = @UniqueConstraint(name = "uk_hostel_room_code", columnNames = "room_code"))
public class HostelRoom {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "room_code", nullable = false, length = 40) private String roomCode;
    @Column(nullable = false, length = 120) private String building;
    @Column(nullable = false) private Integer capacity;
    @Column(name = "available_beds", nullable = false) private Integer availableBeds;
    @Column(nullable = false) private Boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public Integer getAvailableBeds() { return availableBeds; }
    public void setAvailableBeds(Integer availableBeds) { this.availableBeds = availableBeds; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}