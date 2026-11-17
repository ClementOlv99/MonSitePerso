package Siteweb.backend.race;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "races")
public class RaceEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "race_url", nullable = false, unique = true)
    private String raceUrl;          

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;       

    @Column(nullable = false)
    private String category;        

    private String country;          


    protected RaceEntity() {
    }


    public RaceEntity(String raceUrl, String name, LocalDate startDate,
                      LocalDate endDate, String category, String country) {
        this.raceUrl = raceUrl;
        update(name, startDate, endDate, category, country);
    }


    public void update(String name, LocalDate startDate, LocalDate endDate,
                       String category, String country) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.category = category;
        this.country = country;
    }

    public Long getId() { return id; }
    public String getRaceUrl() { return raceUrl; }
    public String getName() { return name; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getCategory() { return category; }
    public String getCountry() { return country; }
}