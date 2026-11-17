package Siteweb.backend.participation;


/**
 * One startlist row sent by Scraper_Manager.py (POST /api/participation).
 * Matches the JSON keys produced by Scraper_Startlist.py:
 * {"team", "rider", "rider_url", "country", "raceUrl"}.
 * "rider" is formatted the PCS way: "LASTNAME Firstname" (e.g. "VAN DER POEL Mathieu").
 */
public record StartListDTO(
        String team,
        String rider,
        String riderUrl,
        String country,
        String raceUrl) {
}
