package Siteweb.backend.race;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

import Siteweb.backend.participation.ParticipationRepository;
import Siteweb.backend.rider.RiderRepository;
import jakarta.transaction.Transactional;

@Service
public class RaceService {

    private final RaceRepository raceRepository;
    private final ParticipationRepository participationRepository;
    private final RiderRepository riderRepository;


    public RaceService(RaceRepository raceRepository, ParticipationRepository participationRepository, RiderRepository riderRepository){
        this.raceRepository = raceRepository;
        this.participationRepository = participationRepository;
        this.riderRepository = riderRepository;
    }


    /**
     * Upserts races by raceUrl: existing races are updated in place (keeping their id),
     * new ones are inserted. Races absent from the import are left untouched, except
     * when the import is the first one for a new year: the table is then cleared
     * before importing, so the previous year's schedule is removed.
     */
    @Transactional
    public int importRaces(List<RaceDTO> dtos) throws IllegalArgumentException {
        if (dtos.isEmpty()) {
            throw new IllegalArgumentException("No races to import");
        }

        if (raceRepository.findAllByYear(dtos.get(0).startDate().getYear()).isEmpty()) {
            participationRepository.deleteAllInBatch();
            raceRepository.deleteAllInBatch();
            riderRepository.deleteAllInBatch();
        }


        Set<String> urls = new HashSet<>();
        for (RaceDTO dto : dtos) {
            urls.add(dto.raceUrl());
        }


        Map<String, RaceEntity> byUrl = new HashMap<>();
        for (RaceEntity race : raceRepository.findAllByRaceUrlIn(urls)) {
            byUrl.put(race.getRaceUrl(), race);
        }

        for (RaceDTO dto : dtos) {
            RaceEntity existing = byUrl.get(dto.raceUrl());
            if (existing != null) {
                existing.update(dto.name(), dto.startDate(), dto.endDate(), dto.category(), dto.country());
            } else {
                byUrl.put(dto.raceUrl(), new RaceEntity(dto.raceUrl(), dto.name(), dto.startDate(),
                        dto.endDate(), dto.category(), dto.country()));
            }
        }

        raceRepository.saveAll(byUrl.values());
        return dtos.size();
    }


    public List<RaceEntity> readRaces(){
        return raceRepository.findAllByOrderByStartDateAsc();
    }



}
