package com.sis.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "transport_routes")
public class TransportRoute {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String stops;
    public TransportRoute() {}
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getStops() { return stops; }
    public void setStops(String value) { stops = value; }
}
