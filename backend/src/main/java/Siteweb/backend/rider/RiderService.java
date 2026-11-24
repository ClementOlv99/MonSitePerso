package Siteweb.backend.rider;

import java.util.List;

import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

@Service
@Transactional
public class RiderService {
    
    private final RiderRepository riderRepository;

    public RiderService(RiderRepository riderRepository) {
        this.riderRepository = riderRepository;
    }
    
    public List<RiderDTO> searchRiders(String query){

        if (query == null || query.trim().length() < 2) {
            return List.of();
        } else {
            query = query.trim();
            List<RiderEntity> riders = riderRepository.findTop10ByNomContainingIgnoreCaseOrPrenomContainingIgnoreCaseOrderByNomAsc(query, query);
            return riders.stream().map(r -> new RiderDTO(r.getId(), r.getRiderUrl(), r.getPrenom(), r.getNom(), r.getCountry())).toList();
        }
    }

    public List<RiderDTO> findByUrls(List<String> urls) {
    return riderRepository.findAllByRiderUrlIn(urls).stream()
            .map(r -> new RiderDTO(r.getId(), r.getRiderUrl(), r.getPrenom(), r.getNom(), r.getCountry()))
            .toList();
    }

}
