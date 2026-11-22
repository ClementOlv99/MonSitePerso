package Siteweb.backend.cards;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mtg_cards")
public class CardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false, unique = true)
    private Integer seed;

    @Column(nullable = false)
    private String name;

    private String manaCost;

    @Column(nullable = false)
    private String typeLine;


    @Column(length = 1000)
    private String oracleText;

    private String power;       
    private String toughness;   
    private String rarity;

    protected CardEntity() {
    }

    public CardEntity(int seed, String name, String manaCost, String typeLine, String oracleText,
                      String power, String toughness, String rarity) {
        this.seed = seed;
        this.name = name;
        this.manaCost = manaCost;
        this.typeLine = typeLine;
        this.oracleText = oracleText;
        this.power = power;
        this.toughness = toughness;
        this.rarity = rarity;
    }

    public Long getId() { return id; }
    public Integer getSeed() { return seed; }
    public String getName() { return name; }
    public String getManaCost() { return manaCost; }
    public String getTypeLine() { return typeLine; }
    public String getOracleText() { return oracleText; }
    public String getPower() { return power; }
    public String getToughness() { return toughness; }
    public String getRarity() { return rarity; }
}
