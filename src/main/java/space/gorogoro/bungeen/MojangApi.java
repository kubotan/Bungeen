package space.gorogoro.bungeen;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;

import org.bukkit.Bukkit;

public class MojangApi {
  
  public static String getUUID(String player) {
    try {
      Integer timestamp = (int) (System.currentTimeMillis()/1000);
      URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + player + "?at=" + timestamp);
      BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
      String Line;
      while ((Line = in .readLine()) != null) {
        String uuid = Line.substring(7, 39);
        return uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + uuid.substring(12, 16) + "-" + uuid.substring(16, 20) + "-" + uuid.substring(20, 32);
      } in .close();
    } catch (Exception e) {
      error(e);
    }
    return null;
  }
  
  public static void error(Exception e) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    pw.flush();
    Bukkit.getLogger().info(sw.toString());
  }

}
