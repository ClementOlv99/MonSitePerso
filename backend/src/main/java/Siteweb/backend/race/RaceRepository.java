package Siteweb.backend.race;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RaceRepository extends JpaRepository<RaceEntity, Long> {
    
    List<RaceEntity> findAllByOrderByStartDateAsc();

    List<RaceEntity> findAllByRaceUrlIn(Collection<String> raceUrls);
}
