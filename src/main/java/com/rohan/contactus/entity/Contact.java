package com.rohan.contactus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "contacts")
public class Contact {

    @Getter
    @Setter
    @Column(name = "name",nullable = false)
    private String name;

    @Id
    @Getter
    @Setter
    @Column(name = "email",nullable = false, unique = true)
    private String email;

    @Getter
    @Setter
    @Column(name = "contact_no",nullable = false)
    private String contactNo;

    @Getter
    @Setter
    @Column(name ="message",nullable = false)
    private String message;
}
