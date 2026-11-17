package Siteweb.backend.participation;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import Siteweb.backend.race.RaceEntity;

@RestController
@RequestMapping("/api/participation")
@CrossOrigin(origins = "http://localhost:5173")
public class ParticipationController {

    ParticipationService participationService;

    public ParticipationController(ParticipationService participationService) {
        this.participationService = participationService;
    }

    // Called by the Python scraper: POST http://localhost:8080/api/participation
    @PostMapping
    public ResponseEntity<Map<String, Integer>> importStartList(@RequestBody List<StartListDTO> startlist) {
        int imported = participationService.importStartlist(startlist);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of("imported", imported));
    }

    // Called by the frontend when a rider is picked: GET http://localhost:8080/api/participation/rider/42
    @GetMapping("/rider/{riderId}")
    public List<RaceEntity> getRiderSchedule(@PathVariable Long riderId) {
        return participationService.getRiderSchedule(riderId);
    }
}
