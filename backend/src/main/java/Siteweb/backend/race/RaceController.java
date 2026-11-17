package Siteweb.backend.race;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/races")
public class RaceController {

    private final RaceService raceService;

    // Spring injects the service automatically (single constructor: no @Autowired needed)
    public RaceController(RaceService raceService) {
        this.raceService = raceService;
    }

    // Called by the Python scraper: POST http://localhost:8080/api/races
    @PostMapping
    public ResponseEntity<Map<String, Integer>> importRaces(@RequestBody List<RaceDTO> races) {
        int imported = raceService.importRaces(races);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of("imported", imported));
    }

    // Called by the frontend: GET http://localhost:8080/api/races
    @GetMapping
    public List<RaceEntity> readRaces() {
        return raceService.readRaces();
    }
}