package Siteweb.backend.race;

import java.time.LocalDate;

public record RaceDTO(Long id, String raceUrl, String name, LocalDate startDate, LocalDate endDate, String category, String country) {}   
