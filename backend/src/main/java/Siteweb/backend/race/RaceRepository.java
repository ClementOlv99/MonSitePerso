package Siteweb.backend.race;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RaceRepository extends JpaRepository<RaceEntity, Long> {
    
    List<RaceEntity> findAllByOrderByStartDateAsc();

    List<RaceEntity> findAllByRaceUrlIn(Collection<String> raceUrls);

    List<RaceEntity> findAllByStartDateBetweenOrderByStartDateAsc(LocalDate start, LocalDate end);

    default List<RaceEntity> findAllByYear(int year) {
        return findAllByStartDateBetweenOrderByStartDateAsc(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
}

    
}
