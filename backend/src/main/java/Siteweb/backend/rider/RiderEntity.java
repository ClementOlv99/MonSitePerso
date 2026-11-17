package Siteweb.backend.rider;

import java.io.Serializable;

import jakarta.persistence.*;

@Entity
@Table(name = "riders")

public class RiderEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rider_url", nullable = false, unique = true)
    private String riderUrl;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String country;

    public RiderEntity() {
    }

    public RiderEntity(String url, String prenom, String nom, String country) {
        this.riderUrl = url;
        this.prenom = prenom;
        this.nom = nom;
        this.country = country;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRiderUrl() {
        return riderUrl;
    }

    public void setRiderUrl(String url) {
        this.riderUrl = url;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}
