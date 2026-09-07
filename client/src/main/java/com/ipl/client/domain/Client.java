package com.ipl.client.domain;
import jakarta.persistence.*;

@Entity
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private String name;

    @Column(nullable=false)
    private String address;

    public Client() {}

    public Client(String n, String a){
        this.name=n;
        this.address=a;
    }

    public Long getId(){
        return id;
    }

    public String getName(){
        return name;
    }

    public void setName(String n){
        this.name=n;
    }

    public String getAddress(){
        return address;
    }

    public void setAddress(String a){
        this.address=a;
    }
}
