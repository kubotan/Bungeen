package space.gorogoro.bungeen;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.plugin.Plugin;

import com.google.gson.Gson;

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
	    if (b.getType() == Material.WALL_SIGN || b.getType() == Material.SIGN_POST) {
	      Sign s = (Sign)b.getState();
	      try {
          if((System.currentTimeMillis() - sv.getFailTimestamp()) > retryclearinterval) {
            sv.setFail(0);
          }
	        if(sv.getFail() != null && sv.getFail() >= retrylimit) {
	          return;
	        }
	        
          String title=sv.getServer();
          String name=sv.getName();
          if(name != null && name.length() > 0) {
            title = name;
          }
          String ip=sv.getIp();
          int port=sv.getPort();

          String comment="";
          if(sv.getComment() != null && sv.getComment().length() > 0) {
            comment = sv.getComment();
          }
          
          s.setLine(0, "§l" + title);
          s.setLine(3, "§l" + comment);
	        if(sv.getType().equals("legacy")) {
	          setSignTextOfPlayersOld(s, ip, port);
	        }else {
	          setSignTextOfPlayers17(s, ip, port);
	        }
	      } catch (Exception e) {
	        error(e);
	        Integer fcnt = sv.getFail();
	        if(fcnt == null) {
	          fcnt = 0;
	        }
	        fcnt++;
	        sv.setFail(fcnt);
	        sv.setFailTimestamp(System.currentTimeMillis());
	        setSignTextofOffline(s);
	      }
	      s.update();
	    }
	  }
  }
  
  public void setSignTextofOffline(Sign s) {
    s.setLine(1, "§a§l- / -");
    s.setLine(2, offline);
  }

  public void setSignTextOfPlayersOld(Sign s,String address,Integer port) throws IOException {
    /*
     * Before client version 1.7
     */
    Socket socket = new Socket(address, port);
    socket.setSoTimeout(3000);

    OutputStream out = socket.getOutputStream();
    out.write(0xFE);
    out.write(0x01);
    out.flush();

    InputStream in = socket.getInputStream();
    if (in.read() != 0xFF) {
      socket.close();
      return;
    }

    byte[] bytes = new byte[2];
    if (in.read(bytes) != 2) {
      socket.close();
      return;
    }
    short len = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getShort();

    bytes = new byte[len * 2];
    in.read(bytes);
    socket.close();
    String response = new String(bytes, Charset.forName("UTF-16BE"));
    String[] data = response.split("\0");

    if (data.length == 1) {
      // Beta 1.8 to 1.3
      data = response.split("\u00A7");
      if (data.length < 3) {
        return;
      }
      int dataLen = data.length;
      String maxPlayers = data[--dataLen];
      String onlinePlayers = data[--dataLen];
      s.setLine(1, "§a§l" + onlinePlayers + " / " + maxPlayers);
      Integer cur = (int) (System.currentTimeMillis() / 1000);
      if(cur % 2 == 0) {
        s.setLine(2, online);
      }else {
        s.setLine(2, blink);
      }
    } else if (data.length == 6) {
      // 1.6
      s.setLine(1, "§a§l" + data[4] + " / " + data[5]);
      Integer cur = (int) (System.currentTimeMillis() / 1000);
      if(cur % 2 == 0) {
        s.setLine(2, online);
      }else {
        s.setLine(2, blink);
      }
    } else {
      return;
    }
  }

  public void setSignTextOfPlayers17(Sign s,String address,Integer port) throws IOException {
    /*
     * Client version 1.7 or later
     */
    Socket socket = new Socket(address, port);
    socket.setSoTimeout(3000);

    DataOutputStream output = new DataOutputStream(socket.getOutputStream());
    DataInputStream input = new DataInputStream(socket.getInputStream());

    byte [] handshakeMessage = createHandshakeMessage(address, port);
    writeVarInt(output, handshakeMessage.length);
    output.write(handshakeMessage);

    output.writeByte(0x01);
    output.writeByte(0x00);

    readVarInt(input);
    int packetId = readVarInt(input);
    if (packetId == -1) {
      socket.close();
      return;
    }
    if (packetId != 0x00) {
      socket.close();
      return;
    }
    int length = readVarInt(input);

    if (length == -1) {
      socket.close();
      return;
    }

    if (length == 0) {
      socket.close();
      return;
    }

    byte[] in = new byte[length];
    input.readFully(in);
    String strJson = new String(in);
    Gson gson = new Gson();
    Status st = gson.fromJson(strJson, Status.class);
    s.setLine(1, "§a§l" + st.getOnlinePlayers() + " / " + st.getMaxPlayers());
    Integer cur = (int) (System.currentTimeMillis() / 1000);
    if(cur % 2 == 0) {
      s.setLine(2, online);
    }else {
      s.setLine(2, blink);
    }

    long now = System.currentTimeMillis();
    output.writeByte(0x09);
    output.writeByte(0x01);
    output.writeLong(now);

    readVarInt(input);
    packetId = readVarInt(input);
    if (packetId == -1) {
      socket.close();
      return;
    }

    if (packetId != 0x01) {
      socket.close();
      return;
    }
    socket.close();    
  }

  public static byte [] createHandshakeMessage(String host, int port) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    DataOutputStream handshake = new DataOutputStream(buffer);
    byte [] bytes = host.getBytes(StandardCharsets.UTF_8);
    handshake.writeByte(0x00);
    writeVarInt(handshake, 4);
    writeVarInt(handshake, bytes.length);
    handshake.write(bytes);
    handshake.writeShort(port);
    writeVarInt(handshake, 1);
    return buffer.toByteArray();
  }
    
  public static void writeVarInt(DataOutputStream out, int paramInt) throws IOException {
    while (true) {
      if ((paramInt & 0xFFFFFF80) == 0) {
        out.writeByte(paramInt);
        return;
      }
      out.writeByte(paramInt & 0x7F | 0x80);
      paramInt >>>= 7;
    }
  }
  
  public static int readVarInt(DataInputStream in) throws IOException {
    int i = 0;
    int j = 0;
    while (true) {
      int k = in.readByte();
      i |= (k & 0x7F) << j++ * 7;
      if (j > 5) {
        throw new IOException("Error: too big");
      }
      if ((k & 0x80) != 128) {
        break;
      }
    }
    return i;
  }
}
