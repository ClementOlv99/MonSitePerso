package Siteweb.backend.rider;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

@Repository
public interface RiderRepository extends JpaRepository<RiderEntity, Long> {

    List<RiderEntity> findTop10ByNomContainingIgnoreCaseOrPrenomContainingIgnoreCaseOrderByNomAsc(
        String lastNamePrefix,
        String firstNamePrefix
    );

    List<RiderEntity> findAllByRiderUrlIn(Collection<String> urls);






}
