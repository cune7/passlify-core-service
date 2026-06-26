package com.passlify.core.event;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    Optional<Location> findByVenueNameAndAddressAndCityAndCountryAndPostalCode(
            String venueName, String address, String city, String country, String postalCode);
}
