package org.byauth.data;

import java.util.ArrayList;
import java.util.List;

public class PlayerStats {

    private int points;
    private int bcoin;
    private int kills;
    private int deaths;
    private int wins;
    private int losses;
    private List<String> ownedCosmetics;
    private String selectedCosmetic;
    private String selectedKillEffect;
    private String selectedArrowEffect;

    public PlayerStats(int points, int bcoin, int kills, int deaths, int wins, int losses, List<String> ownedCosmetics, String selectedCosmetic, String selectedKillEffect, String selectedArrowEffect) {
        this.points = points;
        this.bcoin = bcoin;
        this.kills = kills;
        this.deaths = deaths;
        this.wins = wins;
        this.losses = losses;
        this.ownedCosmetics = ownedCosmetics != null ? ownedCosmetics : new ArrayList<>();
        this.selectedCosmetic = selectedCosmetic;
        this.selectedKillEffect = selectedKillEffect;
        this.selectedArrowEffect = selectedArrowEffect;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public void addPoints(int amount) {
        this.points += amount;
    }

    public int getBCoin() {
        return bcoin;
    }

    public void setBCoin(int bcoin) {
        this.bcoin = bcoin;
    }

    public void addBCoin(int amount) {
        this.bcoin += amount;
    }

    public int getKills() {
        return kills;
    }

    public void incrementKills() {
        this.kills++;
    }

    public int getDeaths() {
        return deaths;
    }

    public void incrementDeaths() {
        this.deaths++;
    }

    public int getWins() {
        return wins;
    }

    public void incrementWins() {
        this.wins++;
    }

    public int getLosses() {
        return losses;
    }

    public void incrementLosses() {
        this.losses++;
    }

    public List<String> getOwnedCosmetics() {
        return ownedCosmetics;
    }

    public void setOwnedCosmetics(List<String> ownedCosmetics) {
        this.ownedCosmetics = ownedCosmetics;
    }

    public String getSelectedCosmetic() {
        return selectedCosmetic;
    }

    public void setSelectedCosmetic(String selectedCosmetic) {
        this.selectedCosmetic = selectedCosmetic;
    }

    public String getSelectedKillEffect() {
        return selectedKillEffect;
    }

    public void setSelectedKillEffect(String selectedKillEffect) {
        this.selectedKillEffect = selectedKillEffect;
    }

    public String getSelectedArrowEffect() {
        return selectedArrowEffect;
    }

    public void setSelectedArrowEffect(String selectedArrowEffect) {
        this.selectedArrowEffect = selectedArrowEffect;
    }
}