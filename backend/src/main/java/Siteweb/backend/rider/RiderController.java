package Siteweb.backend.rider;

import java.util.List;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rider")
public class RiderController {

    private final RiderService riderService;

    public RiderController(RiderService riderService) {
        this.riderService = riderService;
    }

    // Called by the search bar: GET http://localhost:8080/api/rider/search?q=pog
    @GetMapping("/search")
    public List<RiderDTO> searchRiders(@RequestParam("q") String q) {
        return riderService.searchRiders(q);
    }
}
