package space.gorogoro.bungeen;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class SignUpdater implements Runnable {
  public String online;
  public String offline;
  public String blink;
  public Plugin bungeen;
  public Connection con;
  public ArrayList<Server> serverList = new ArrayList<Server>();
  public int retrylimit;
  public int retryclearinterval;
  
  public SignUpdater(Connection connection) {
    try {
      this.con = connection;
      this.bungeen = Bukkit.getPluginManager().getPlugin("Bungeen");
      this.online = this.bungeen.getConfig().getString("message.online");
      this.offline = this.bungeen.getConfig().getString("message.offline");
      this.blink = this.bungeen.getConfig().getString("message.blink");
      this.retrylimit = this.bungeen.getConfig().getInt("setting.retrylimit");
      this.retryclearinterval = this.bungeen.getConfig().getInt("setting.retryclearinterval");
      PreparedStatement prepStmt1 = con.prepareStatement("select world,x,y,z,ip,port,server,type,name,comment from bungeen;");
      ResultSet rs = prepStmt1.executeQuery();
      while (rs.next()) {
        Server sv = new Server();
        sv.setWorld(rs.getString(1));
        sv.setX(rs.getInt(2));
        sv.setY(rs.getInt(3));
        sv.setZ(rs.getInt(4));
        sv.setIp(rs.getString(5));
        sv.setPort(rs.getInt(6));
        sv.setServer(rs.getString(7));
        sv.setType(rs.getString(8));
        sv.setName(rs.getString(9));
        sv.setComment(rs.getString(10));
        this.serverList.add(sv);
      }
      rs.close();
      prepStmt1.close();

    } catch (SQLException e) {
      error(e);
    }
  }
  
  public void error(Exception e) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    pw.flush();
    Bukkit.getLogger().info(sw.toString());
  }
  
  @Override
  public void run() {
    for(int i = 0;i< this.serverList.size();i++) {
      Server sv = this.serverList.get(i);	    
	    Block b = Bukkit.getWorld(sv.getWorld()).getBlockAt(sv.getX(), sv.getY(), sv.getZ());
	    boolean isNearPlayer = false;
	    for(Entity e: b.getLocation().getChunk().getEntities()) {
	    		if(e instanceof Player) {
	    			isNearPlayer = true;
	    			break;
	    		}
	    }
	    if(isNearPlayer == false) {
	    		continue;
	    }
	    if (b.getType() == Material.WALL_SIGN || b.getType() == Material.SIGN) {
	      Sign s = (Sign)b.getState();
        String title=sv.getServer();
        String name=sv.getName();
        if(name != null && name.length() > 0) {
          title = name;
        }
        String comment="";
        if(sv.getComment() != null && sv.getComment().length() > 0) {
          comment = sv.getComment();
        }
	      try {
          if(sv.getFail() != null && sv.getFail() > 0 && (System.currentTimeMillis() - sv.getFailTimestamp()) > retryclearinterval) {
            sv.setFail(0);
          }
	        if(sv.getFail() != null && sv.getFail() >= retrylimit) {
	          return;
	        }
	        
          String ip=sv.getIp();
          int port=sv.getPort();
          
          MinecraftClient mc = new MinecraftClient();
          Status st = mc.getStatus(ip, port);
          s.setLine(0, "§l" + title);
          s.setLine(1, "§a§l" + st.getOnlinePlayers() + " / " + st.getMaxPlayers());
          Integer cur = (int) (System.currentTimeMillis() / 1000);
          if(cur % 2 == 0) {
            s.setLine(2, online);
          }else {
            s.setLine(2, blink);
          }
          s.setLine(3, "§l" + comment);

	      } catch (Exception e) {
	        Integer fcnt = sv.getFail();
	        if(fcnt == null) {
	          fcnt = 0;
	        }
	        fcnt++;
	        sv.setFail(fcnt);
	        sv.setFailTimestamp(System.currentTimeMillis());
          s.setLine(0, "§l" + title);
	        s.setLine(1, "§a§l- / -");
	        s.setLine(2, offline);
          s.setLine(3, "§l" + comment);
	      }
	      s.update();
	    }
	  }
  }
}
