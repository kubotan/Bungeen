package space.gorogoro.bungeen;

public class Status {
  
  public Players players;
  
  public Integer getOnlinePlayers() {
    return this.players.online;
  }

  public Integer getMaxPlayers() {
    return this.players.max;
  }

  public class Players {
    private int max;
    private int online;
  }
}
