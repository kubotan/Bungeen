package space.gorogoro.bungeen;

public class Status {
  
  public Players players;
  public Integer ping;

  public Integer getPing() {
    return this.ping;
  }

  public void setPing(Integer i) {
    this.ping = i;
  }
  
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
