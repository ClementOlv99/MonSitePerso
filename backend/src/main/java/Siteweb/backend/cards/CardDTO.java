package Siteweb.backend.cards;

// What the frontend receives: GET /api/cards/random, GET /api/cards/{seed}
public record CardDTO(
        int seed,
        String name,
        String manaCost,
        String typeLine,
        String oracleText,
        String power,
        String toughness,
        String rarity) {

    public static CardDTO from(CardEntity card) {
        return new CardDTO(card.getSeed(), card.getName(), card.getManaCost(), card.getTypeLine(),
                card.getOracleText(), card.getPower(), card.getToughness(), card.getRarity());
    }
}
