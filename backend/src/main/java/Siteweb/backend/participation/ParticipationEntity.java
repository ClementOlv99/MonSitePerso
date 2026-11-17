package Siteweb.backend.participation;

import Siteweb.backend.race.RaceEntity;
import Siteweb.backend.rider.RiderEntity;
import jakarta.persistence.*;

@Entity
@Table(
    name = "participations",
    uniqueConstraints = @UniqueConstraint(columnNames = {"rider_url", "race_url"})
)
public class ParticipationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_url", referencedColumnName = "race_url", nullable = false)
    private RaceEntity race;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rider_url", referencedColumnName = "rider_url", nullable = false)
    private RiderEntity rider;

    @Column(nullable = false)
    private String team;


    protected ParticipationEntity() {
    }


    public ParticipationEntity(RaceEntity race, RiderEntity rider, String team) {
        this.race = race;
        this.rider = rider;
        this.team = team;
    }


    public Long getId() { return id; }
    public RaceEntity getRace() { return race; }
    public RiderEntity getRider() { return rider; }
    public String getTeam() { return team; }

    public void setTeam(String team) { this.team = team; }
}
