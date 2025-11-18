package com.rohan.contactus.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class RefreshToken {

    @Id
    private Long id; // The primary key (PK)

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    // Use @MapsId to declare that the PK is shared/derived from the User entity.
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id") // The foreign key column is named 'id' in the refresh_token table
    private User user;

    // NOTE: If you are using Lombok and Hibernate 6.x, sometimes
    // the default constructor is needed for entity instantiation.
    public RefreshToken() {}
}