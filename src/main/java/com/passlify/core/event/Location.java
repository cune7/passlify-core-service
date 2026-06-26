package com.passlify.core.event;

import com.passlify.core.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** A physical venue, reusable across events. */
@Getter
@Setter
@Entity
@Table(name = "location")
public class Location extends BaseEntity {

    @Column(name = "venue_name", nullable = false, length = 255)
    private String venueName;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
