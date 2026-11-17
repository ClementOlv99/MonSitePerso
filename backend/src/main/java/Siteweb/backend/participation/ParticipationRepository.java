package Siteweb.backend.participation;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import Siteweb.backend.race.RaceEntity;

@Repository
public interface ParticipationRepository extends JpaRepository<ParticipationEntity, Long> {

    @Query("select concat(p.race.raceUrl, '|', p.rider.riderUrl) from ParticipationEntity p where p.race.raceUrl in :raceUrls")
    List<String> findExistingPairs(@Param("raceUrls") Collection<String> raceUrls);

    // A rider's schedule: every race they're on the startlist of, in date order.
    // Selecting p.race returns real RaceEntity objects (not lazy proxies), in a single SQL query.
    @Query("select p.race from ParticipationEntity p where p.rider.id = :riderId order by p.race.startDate")
    List<RaceEntity> findRacesByRiderId(@Param("riderId") Long riderId);
}
