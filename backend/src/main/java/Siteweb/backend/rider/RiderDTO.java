package Siteweb.backend.rider;



public record RiderDTO(
        Long id,
        String riderUrl,
        String prenom,
        String nom,
        String country) {
}
