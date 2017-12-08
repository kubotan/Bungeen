package space.gorogoro.bungeen;
sdaafa
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

      // テーブルの実在チェック
      Boolean existsBungeenTable = false;
      ResultSet rs1 = stmt.executeQuery("select count(*) from sqlite_master where type='table' and name='bungeen'");
      while (rs1.next()) {
        if(rs1.getInt(1) > 0){
          existsBungeenTable = true;
        }
      }
      rs1.close();
      Boolean existsMemberTable = false;
      ResultSet rs2 = stmt.executeQuery("select count(*) from sqlite_master where type='table' and name='member'");
      while (rs2.next()) {
        if(rs2.getInt(1) > 0){
          existsMemberTable = true;
        }
      }
      rs2.close();

      // テーブルが無かった場合
      if(!existsBungeenTable){
        //テーブル作成
        stmt.executeUpdate("create table bungeen ("
          + "id integer primary key autoincrement,"
          + "world string not null,"
          + "x integer not null,"
          + "y integer not null,"
          + "z integer not null,"
          + "server string not null,"
          + "ip string not null,"
          + "port integer not null,"
          + "type string not null default 'default',"
          + "name string,"
          + "comment string);"
        );

        //インデックス作成
        stmt.executeUpdate("create index bungeen_world_x_y_z on bungeen (world,x,y,z);");
        stmt.executeUpdate("create index bungeen_server on bungeen (server);");
      }
      if(!existsMemberTable){
        //テーブル作成
        stmt.executeUpdate("create table member ("
          + "id integer primary key autoincrement,"
          + "bungeen_id integer not null,"
          + "owner string not null,"
          + "member string not null,"
          + "owner_uuid string not null,"
          + "member_uuid string not null);"
        );

        //インデックス作成
        stmt.executeUpdate("create index member_bungeen_id on member (bungeen_id);");
      }
      
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

      Player mp;
      String server,member,owner,memberUuid,ownerUuid;
      Integer memberId = null;
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
        
        case "setprotocoltype":
          if ( args.length < 2) {
            sp.sendMessage("§cThe server name is required as an argument.");
            return false;            
          }
          server = args[1];

          if ( args.length < 3) {
            sp.sendMessage("§cThe protocol type value is required as an argument.");
            return false;            
          }
          String type = args[2];
          if((!type.equals("default") && !type.equals("legacy"))) {
            sp.sendMessage("§cThe value is required as an argument. default or legacy");
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

          prepStmt = con.prepareStatement("update bungeen set type = ? where id = ?");
          prepStmt.setString(1, type);
          prepStmt.setInt(2, bungeenId);
          prepStmt.addBatch();
          prepStmt.executeBatch();
          con.commit();
          prepStmt.close();
          sp.sendMessage("Protocol type updated!");
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
          
          mp = this.getServer().getPlayer(member);
          if(mp == null) {
            memberUuid = MojangApi.getUUID(member);
            if(memberUuid == null) {
              sp.sendMessage("§cCan't find member.The member to be added is not online.");
              return false;
            }
          } else {
            memberUuid = mp.getUniqueId().toString();
          }
          owner = sp.getName();
          ownerUuid = sp.getUniqueId().toString();
          if(member.length() < 1 || 
            owner.length() < 1 ||
            memberUuid.length() < 1 || 
            ownerUuid.length() < 1) {
            sp.sendMessage("§cCan't find player info.");
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
            return true;
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

          mp = this.getServer().getPlayer(member);
          if(mp == null) {
            memberUuid = MojangApi.getUUID(member);
            if(memberUuid == null) {
              sp.sendMessage("§cCan't find member.The member to be deleted is not online.");
              return false;
            }
          } else {
            memberUuid = mp.getUniqueId().toString();
          }
          owner = sp.getName();
          ownerUuid = sp.getUniqueId().toString();
          if(member.length() < 1 || 
            owner.length() < 1 ||
            memberUuid.length() < 1 || 
            ownerUuid.length() < 1) {
            sp.sendMessage("§cCan't find player info.");
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
            return false;
          }

          prepStmt = con.prepareStatement("delete from member where bungeen_id=? and member_uuid=?;");
          prepStmt.setInt(1, bungeenId);
          prepStmt.setString(2, memberUuid);
          prepStmt.addBatch();
          prepStmt.executeBatch();
          con.commit();
          prepStmt.close();
          sp.sendMessage("Delete member!");
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
    if (material != Material.SIGN_POST && material != Material.WALL_SIGN) {
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

