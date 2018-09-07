package space.gorogoro.bungeen;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class Bungeen extends JavaPlugin implements Listener {

  private Connection con;
  private BukkitScheduler scheduler;

  public void onEnable() {
    getServer().getPluginManager().registerEvents(this, this);
    Bukkit.getLogger().info("The Plugin Has Been Enabled!");

    try{
      // registerOutgoingPluginChannel
      Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

      // create configure file.
      File configFile = new File(this.getDataFolder() + "/config.yml");
      if(!configFile.exists()){
        saveDefaultConfig();
      }

      // JDBCドライバーの指定
      Class.forName("org.sqlite.JDBC");

      // データベースに接続する なければ作成される
      con = DriverManager.getConnection("jdbc:sqlite:" + this.getDataFolder() + "/bungeen.db");
      con.setAutoCommit(false);      // auto commit無効

      // Statementオブジェクト作成
      Statement stmt = con.createStatement();
      stmt.setQueryTimeout(30);    // タイムアウト設定

      //テーブル作成
      stmt.executeUpdate("CREATE TABLE IF NOT EXISTS bungeen ("
        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
        + "world STRING NOT NULL,"
        + "x INTEGER NOT NULL,"
        + "y INTEGER NOT NULL,"
        + "z INTEGER NOT NULL,"
        + "server STRING NOT NULL,"
        + "ip STRING NOT NULL,"
        + "port INTEGER NOT NULL,"
        + "name STRING,"
        + "comment STRING);"
      );

      //インデックス作成
      stmt.executeUpdate("CREATE INDEX IF NOT EXISTS bungeen_world_x_y_z ON bungeen (world,x,y,z);");
      stmt.executeUpdate("CREATE INDEX IF NOT EXISTS bungeen_server ON bungeen (server);");

      //テーブル作成
      stmt.executeUpdate("CREATE TABLE IF NOT EXISTS member ("
        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
        + "bungeen_id INTEGER NOT NULL,"
        + "owner STRING NOT NULL,"
        + "member STRING NOT NULL,"
        + "owner_uuid STRING NOT NULL,"
        + "member_uuid STRING NOT NULL);"
      );

      //インデックス作成
      stmt.executeUpdate("CREATE INDEX IF NOT EXISTS member_bungeen_id ON member (bungeen_id);");
      
      stmt.close();
      
      scheduler = getServer().getScheduler();
      scheduler.scheduleSyncRepeatingTask(this, new SignUpdater(con), 0L, 20L);

    } catch (SQLException e) {
      error(e);
    } catch (Exception e){
      error(e);
    }
  }

  @Override
  public boolean onCommand( CommandSender sender, Command command, String label, String[] args) {
    try{
      if ( !command.getName().equals("bungeen") ) {
        return true;
      }

      if(!(sender instanceof Player)) {
        return true;
      }

      Player sp = (Player)sender;
      if(!sp.isOp()) {
        sp.sendMessage("§cYou don't have permission.");
        return true;
      }

      String server,member;
      Integer bungeenId = null;
      PreparedStatement prepStmt;
      ResultSet rs;
      if(args.length < 1) {
        sp.sendMessage("§cThe sub-command is required as an argument.");
        return false;
      }
      String subCommand = args[0];
      switch(subCommand) {
        case "reload":
          scheduler.cancelTasks(this);
          scheduler.scheduleSyncRepeatingTask(this, new SignUpdater(con), 0L, 20L);
          sp.sendMessage("reload!");
          break;
          
        case "remove":
          if ( args.length < 2) {
            sp.sendMessage("§cThe server name is required as an argument.");
            return false;            
          }
          server = args[1];
          
          prepStmt = con.prepareStatement("select id from bungeen where server=?;");
          prepStmt.setString(1, server);
          rs = prepStmt.executeQuery();
          while (rs.next()) {
            bungeenId = rs.getInt(1);
          }
          rs.close();
          prepStmt.close();
          if(bungeenId == null) {
            sp.sendMessage("§cCan't find server.");
            return false;
          }
          
          prepStmt = con.prepareStatement("delete from bungeen where id = ?");
          prepStmt.setInt(1, bungeenId);
          prepStmt.addBatch();
          prepStmt.executeBatch();
          con.commit();
          prepStmt.close();
          sp.sendMessage("Deleted!");
          sp.sendMessage("§cPlease reflect it with '/bungeen reload'.");
          break;

        case "setname":
          if ( args.length < 2) {
            sp.sendMessage("§cThe server name is required as an argument.");
            return false;            
          }
          server = args[1];  
          
          if ( args.length < 3) {
            sp.sendMessage("§cThe display name value is required as an argument.");
            return false;            
          }
          
          prepStmt = con.prepareStatement("select id from bungeen where server=?;");
          prepStmt.setString(1, server);
          rs = prepStmt.executeQuery();
          while (rs.next()) {
            bungeenId = rs.getInt(1);
          }
          rs.close();
          prepStmt.close();
          if(bungeenId == null) {
            sp.sendMessage("§cCan't find server.");
            return false;
          }

          List<String> names = new ArrayList<String>();
          for(int i=2;i < args.length;i++) {
            names.add(args[i]);
          }
          String name = String.join(" ", names);
          prepStmt = con.prepareStatement("update bungeen set name = ? where id = ?");
          prepStmt.setString(1, name);
          prepStmt.setInt(2, bungeenId);
          prepStmt.addBatch();
          prepStmt.executeBatch();
          con.commit();
          prepStmt.close();
          sp.sendMessage("Name updated!");
          sp.sendMessage("§cPlease reflect it with '/bungeen reload'.");
          break;
          
        case "setcomment":
          if ( args.length < 2) {
            sp.sendMessage("§cThe server name is required as an argument.");
            return false;            
          }
          server = args[1];
          if ( args.length < 3) {
            sp.sendMessage("§cThe comment value is required as an argument.");
            return false;            
          }
          
          prepStmt = con.prepareStatement("select id from bungeen where server=?;");
          prepStmt.setString(1, server);
          rs = prepStmt.executeQuery();
          while (rs.next()) {
            bungeenId = rs.getInt(1);
          }
          rs.close();
          prepStmt.close();
          if(bungeenId == null) {
            sp.sendMessage("§cCan't find server.");
            return false;
          }

          List<String> comments = new ArrayList<String>();
          for(int i=2;i < args.length;i++) {
            comments.add(args[i]);
          }
          String comment = String.join(" ", comments);
          prepStmt = con.prepareStatement("update bungeen set comment = ? where id = ?");
          prepStmt.setString(1, comment);
          prepStmt.setInt(2, bungeenId);
          prepStmt.addBatch();
          prepStmt.executeBatch();
          con.commit();
          prepStmt.close();
          sp.sendMessage("Comment updated!");
          sp.sendMessage("§cPlease reflect it with '/bungeen reload'.");
          break;
          
        case "addmember":
          if ( args.length < 2) {
            sp.sendMessage("§cThe server name is required as an argument.");
            return false;            
          }
          server = args[1];
          if ( args.length < 3) {
            sp.sendMessage("§cThe member name value is required as an argument.");
            return false;            
          }
          member = args[2];
          Player addMp = this.getServer().getPlayer(member);

          new BukkitRunnable() {
            String server,member,owner,memberUuid,ownerUuid;
            Integer memberId = null;
            Integer bungeenId = null;
            PreparedStatement prepStmt;
            ResultSet rs;

          		@Override
        			public void run() {
  		          try {
			          if(addMp == null) {
			            memberUuid = MojangApi.getUUID(member);
			            if(memberUuid == null) {
			              sp.sendMessage("§cCan't find member.The member to be added is not online.");
			              return;
			            }
			          } else {
			            memberUuid = addMp.getUniqueId().toString();
			          }
			          owner = sp.getName();
			          ownerUuid = sp.getUniqueId().toString();
			          if(member.length() < 1 || 
			            owner.length() < 1 ||
			            memberUuid.length() < 1 || 
			            ownerUuid.length() < 1) {
			            sp.sendMessage("§cCan't find player info.");
			            return;
			          }
			          
	              prepStmt = con.prepareStatement("select id from bungeen where server=?;");
			          prepStmt.setString(1, server);
			          rs = prepStmt.executeQuery();
			          while (rs.next()) {
			            bungeenId = rs.getInt(1);
			          }
			          rs.close();
			          prepStmt.close();
			          if(bungeenId == null) {
			            sp.sendMessage("§cCan't find server.");
			            return;
			          }
			
			          prepStmt = con.prepareStatement("select id from member where bungeen_id = ? and member_uuid=?;");
			          prepStmt.setInt(1, bungeenId);
			          prepStmt.setString(2, memberUuid);
			          rs = prepStmt.executeQuery();
			          while (rs.next()) {
			            memberId = rs.getInt(1);
			          }
			          rs.close();
			          prepStmt.close();
			          if(memberId != null) {
			            sp.sendMessage("§cAlready member.");
			            return;
			          }
			
			          prepStmt = con.prepareStatement("insert into member(bungeen_id,owner,member,owner_uuid,member_uuid) values (?,?,?,?,?);");
			          prepStmt.setInt(1, bungeenId);
			          prepStmt.setString(2, owner);
			          prepStmt.setString(3, member);
			          prepStmt.setString(4, ownerUuid);
			          prepStmt.setString(5, memberUuid);
			          prepStmt.addBatch();
			          prepStmt.executeBatch();
			          con.commit();
			          prepStmt.close();
			          sp.sendMessage("Add member!");
  							} catch (SQLException e) {
  								e.printStackTrace();
  							}

        			}
          }.runTaskAsynchronously(this);
          break;
          
        case "delmember":
          if ( args.length < 2) {
            sp.sendMessage("§cThe server name is required as an argument.");
            return false;            
          }
          server = args[1];
          if ( args.length < 3) {
            sp.sendMessage("§cThe member name value is required as an argument.");
            return false;            
          }
          member = args[2];

          Player delMp = this.getServer().getPlayer(member);
          
          new BukkitRunnable() {
            String server,member,owner,memberUuid,ownerUuid;
            Integer memberId = null;
            Integer bungeenId = null;
            PreparedStatement prepStmt;
            ResultSet rs;

          		@Override
        			public void run() {
  		          try {
			          if(delMp == null) {
			            memberUuid = MojangApi.getUUID(member);
			            if(memberUuid == null) {
			              sp.sendMessage("§cCan't find member.The member to be deleted is not online.");
			              return;
			            }
			          } else {
			            memberUuid = delMp.getUniqueId().toString();
			          }
			          owner = sp.getName();
			          ownerUuid = sp.getUniqueId().toString();
			          if(member.length() < 1 || 
			            owner.length() < 1 ||
			            memberUuid.length() < 1 || 
			            ownerUuid.length() < 1) {
			            sp.sendMessage("§cCan't find player info.");
			            return;
			          }
			
			          prepStmt = con.prepareStatement("select id from bungeen where server=?;");
			          prepStmt.setString(1, server);
			          rs = prepStmt.executeQuery();
			          while (rs.next()) {
			            bungeenId = rs.getInt(1);
			          }
			          rs.close();
			          prepStmt.close();
			          
			          if(bungeenId == null) {
			            sp.sendMessage("§cCan't find server.");
			            return;
			          }
			
			          prepStmt = con.prepareStatement("select id from member where bungeen_id = ? and member_uuid=?;");
			          prepStmt.setInt(1, bungeenId);
			          prepStmt.setString(2, memberUuid);
			          rs = prepStmt.executeQuery();
			          while (rs.next()) {
			            memberId = rs.getInt(1);
			          }
			          rs.close();
			          prepStmt.close();
			          if(memberId == null) {
			            sp.sendMessage("§cCan't find member.");
			            return;
			          }
			
			          prepStmt = con.prepareStatement("delete from member where bungeen_id=? and member_uuid=?;");
			          prepStmt.setInt(1, bungeenId);
			          prepStmt.setString(2, memberUuid);
			          prepStmt.addBatch();
			          prepStmt.executeBatch();
			          con.commit();
			          prepStmt.close();
			          sp.sendMessage("Delete member!");
  							} catch (SQLException e) {
  								e.printStackTrace();
  							}

        			}
          }.runTaskAsynchronously(this);
          break;
          
        case "delallmember":
          if ( args.length < 2) {
            sp.sendMessage("§cThe server name is required as an argument.");
            return false;            
          }
          server = args[1];

          prepStmt = con.prepareStatement("select id from bungeen where server=?;");
          prepStmt.setString(1, server);
          rs = prepStmt.executeQuery();
          while (rs.next()) {
            bungeenId = rs.getInt(1);
          }
          rs.close();
          prepStmt.close();
          
          if(bungeenId == null) {
            sp.sendMessage("§cCan't find server.");
            return false;
          }

          prepStmt = con.prepareStatement("delete from member where bungeen_id=?;");
          prepStmt.setInt(1, bungeenId);
          prepStmt.addBatch();
          prepStmt.executeBatch();
          con.commit();
          prepStmt.close();
          sp.sendMessage("Delete all member!");
          break;

        default:
          sp.sendMessage("§cUnknown subcommand.");
          return false;
      }
    } catch (SQLException e) {
      error(e);
    } catch (Exception e){
      error(e);
    }
    return true;
  }
  
  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
      return;
    }
    Block clickedBlock = event.getClickedBlock();
    Material material = clickedBlock.getType();
    if (material != Material.SIGN && material != Material.WALL_SIGN) {
      return;
    }
    
    try {
      Integer bungeenId = null;
      String server = null;
      Sign sign = (Sign) clickedBlock.getState();
      Location signLoc = sign.getLocation();        
      String world = signLoc.getWorld().getName();
      Integer x = signLoc.getBlockX();
      Integer y = signLoc.getBlockY();
      Integer z = signLoc.getBlockZ();
      PreparedStatement prepStmt1 = con.prepareStatement("select id,server from bungeen where world=? and x=? and y=? and z=?;");
      prepStmt1.setString(1, world);
      prepStmt1.setInt(2, x);
      prepStmt1.setInt(3, y);
      prepStmt1.setInt(4, z);
      ResultSet rs = prepStmt1.executeQuery();
      while (rs.next()) {
        bungeenId = rs.getInt(1);
        server = rs.getString(2);
      }
      rs.close();
      prepStmt1.close();
      if(bungeenId == null || server == null) {
        return;
      }
      
      Player p = event.getPlayer();
      String memberUuid = p.getUniqueId().toString();
      if(memberUuid.length() < 1) {
        p.sendMessage("Can't find player.");
        return;
      }
      ArrayList<String> memberUuidList = new ArrayList<String>();
      PreparedStatement prepStmt2 = con.prepareStatement("select member_uuid from member where bungeen_id = ?;");
      prepStmt2.setInt(1, bungeenId);
      ResultSet rs2 = prepStmt2.executeQuery();
      while (rs2.next()) {
        memberUuidList.add(rs2.getString(1));
      }
      rs2.close();
      prepStmt2.close();
      if(memberUuidList.size() > 0) {
        if(!memberUuidList.contains(memberUuid)) {
          p.sendMessage(getConfig().getString("message.deny"));
          return;
        }
      }
      
      String msg = getConfig().getString("message.anotherserver").replace("%player%", p.getName().toString());
      for(Player player : Bukkit.getOnlinePlayers()) {
        player.sendMessage(msg);
      }
      p.sendMessage("§cConnecting...");
      ByteArrayDataOutput out = ByteStreams.newDataOutput();
      out.writeUTF("Connect");
      out.writeUTF(server);
      event.getPlayer().sendPluginMessage(this, "BungeeCord", out.toByteArray());
    } catch (SQLException e) {
      error(e);
    } catch (Exception e) {
      error(e);
      event.getPlayer().sendMessage(getConfig().getString("message.coolingdown"));
    }
  }
  
  @EventHandler
  public void onSignChange(SignChangeEvent event) {    
    try {
      if(!event.getLine(0).toLowerCase().equals("[bungeen]")) {
        return;
      }
      String server = event.getLine(1);
      String ip = event.getLine(2);
      String strPort = event.getLine(3);
      int port = 25565;
      if(strPort != null && strPort.length() > 0) {
        port = Integer.parseInt(strPort);
      }

      Integer bungeenId = null;
      Location signLoc = event.getBlock().getLocation();
      String world = signLoc.getWorld().getName();
      Integer x = signLoc.getBlockX();
      Integer y = signLoc.getBlockY();
      Integer z = signLoc.getBlockZ();
      PreparedStatement prepStmt1 = con.prepareStatement("select id from bungeen where world=? and x=? and y=? and z=?");
      prepStmt1.setString(1, world);
      prepStmt1.setInt(2, x);
      prepStmt1.setInt(3, y);
      prepStmt1.setInt(4, z);
      ResultSet rs = prepStmt1.executeQuery();
      while (rs.next()) {
        bungeenId = rs.getInt(1);
      }
      rs.close();
      prepStmt1.close();

      if(bungeenId != null ||
        server.length() < 1 ||
        ip.length() < 1) {
        return;
      }

      PreparedStatement prepStmt2 = con.prepareStatement("insert into bungeen (world,x,y,z,server,ip,port) values (?,?,?,?,?,?,?);");
      prepStmt2.setString(1, world);
      prepStmt2.setInt(2, x);
      prepStmt2.setInt(3, y);
      prepStmt2.setInt(4, z);
      prepStmt2.setString(5, server);
      prepStmt2.setString(6, ip);
      prepStmt2.setInt(7, port);
      prepStmt2.addBatch();
      prepStmt2.executeBatch();
      con.commit();
      prepStmt2.close();
      
      event.getPlayer().sendMessage("§cPlease reflect it with '/bungeen reload'.");
      
    } catch (SQLException e) {
      error(e);
    } catch (Exception e){
      error(e);
    }
  }

  @Override
  public void onDisable(){
    try{
      // DB切断
      if (con != null) {
        con.close();
      }
    } catch (SQLException e) {
      error(e);
    } catch (Exception e){
      error(e);
    }
    Bukkit.getLogger().info("The Plugin Has Been Disabled!");
  }

  public void error(Exception e) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    pw.flush();
    Bukkit.getLogger().info(sw.toString());
  }
}

