package Siteweb.backend.participation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import Siteweb.backend.race.RaceDTO;
import Siteweb.backend.race.RaceEntity;
import Siteweb.backend.race.RaceRepository;
import Siteweb.backend.rider.*;
import jakarta.transaction.Transactional;

@Service
@Transactional
public class ParticipationService {

    private ParticipationRepository participationRepository;

    private RiderRepository riderRepository;

    private RaceRepository raceRepository;

    public ParticipationService(ParticipationRepository participationRepository, RiderRepository riderRepository, RaceRepository raceRepository) {
        this.participationRepository = participationRepository;
        this.riderRepository = riderRepository;
        this.raceRepository = raceRepository;
    }




    public int importStartlist(List<StartListDTO> entries) {
        if (entries.isEmpty()) {
            return 0;
        }

        Set<String> raceUrls = new HashSet<>();
        Set<String> riderUrls = new HashSet<>();
        for (StartListDTO entry : entries) {
            raceUrls.add(entry.raceUrl());
            riderUrls.add(entry.riderUrl());
        }


        Map<String, RaceEntity> raceByUrl = new HashMap<>();
        for (RaceEntity race : raceRepository.findAllByRaceUrlIn(raceUrls)) {
            raceByUrl.put(race.getRaceUrl(), race);
        }

        Map<String, RiderEntity> riderByUrl = new HashMap<>();
        for (RiderEntity rider : riderRepository.findAllByRiderUrlIn(riderUrls)) {
            riderByUrl.put(rider.getRiderUrl(), rider);
        }

        Set<String> existingPairs = new HashSet<>(participationRepository.findExistingPairs(raceUrls));


        List<RiderEntity> newRiders = new ArrayList<>();
        List<ParticipationEntity> newParticipations = new ArrayList<>();

        for (StartListDTO entry : entries) {
            RaceEntity currentRace = raceByUrl.get(entry.raceUrl());

            if (currentRace == null) {
                continue; 
            }

            RiderEntity currentRider = riderByUrl.get(entry.riderUrl());
            if (currentRider == null) {
                List<String> nomParts = new ArrayList<>();
                List<String> prenomParts = new ArrayList<>();
                for (String word : entry.rider().trim().split("\\s+")) {
                    if (word.equals(word.toUpperCase())) {
                        nomParts.add(word);
                    } else {
                        prenomParts.add(word);
                    }
                }
                String nom = String.join(" ", nomParts);
                String prenom = String.join(" ", prenomParts);

                currentRider = new RiderEntity(entry.riderUrl(), prenom, nom, entry.country());
                riderByUrl.put(entry.riderUrl(), currentRider); // avoid creating the same rider twice
                newRiders.add(currentRider);
            }


            String key = entry.raceUrl() + "|" + entry.riderUrl();
            if (!existingPairs.add(key)) {
                continue; 
            }

            newParticipations.add(new ParticipationEntity(currentRace, currentRider, entry.team()));
        }


        riderRepository.saveAll(newRiders);
        participationRepository.saveAll(newParticipations);


        return newParticipations.size();
    }

    public List<RaceEntity> getRiderSchedule(Long riderId){
        return participationRepository.findRacesByRiderId(riderId);
    }


}
