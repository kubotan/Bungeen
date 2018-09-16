package space.gorogoro.bungeen;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;

public class MinecraftClient {

  public Status getStatus(String strHostName, int intPort) throws Exception {
      AsyncClient client = new AsyncClient(strHostName, intPort);
      client.start();
      byte[] sendStr = getHandshakeAndEmptyLoginMessage(strHostName, intPort);
      byte[] response = client.sendMessage(sendStr);
      if( response == null) {
      		throw new IOException("No response.");
      }
      ByteArrayInputStream buffer = new ByteArrayInputStream(response);
      DataInputStream in = new DataInputStream(buffer);
      readVarInt(in);
      int packetId = readVarInt(in);
      if (packetId == -1) {
        throw new IOException("Premature end of stream.");
      }
      if (packetId != 0x00) {
        throw new IOException("Invalid packetID.(JsonResponse)");
      }
      int lengthJson = readVarInt(in); //length of json string
      if (lengthJson == -1) {
        throw new IOException("Premature end of stream.");
      }
      if (lengthJson == 0) {
        throw new IOException("Invalid string length.");
      }
      byte[] b = new byte[lengthJson];
      in.readFully(b);
      String json = new String(b, "UTF-8");
      
      // ----- Ping Start ------
      client.setWaitFlag(false);
      long now = System.currentTimeMillis();
      byte[] pingMessage = getPingMessage(now);
      byte[] rt = client.sendMessage(pingMessage);
      ByteArrayInputStream buffer2 = new ByteArrayInputStream(rt);
      DataInputStream in2 = new DataInputStream(buffer2);
      readVarInt(in2);
      packetId = readVarInt(in2);
      if (packetId == -1) {
        throw new IOException("Premature end of stream.");
      }
      if (packetId != 0x01) {
        throw new IOException("Invalid packetID.(ping)");
      }
      long sendTime = in2.readLong(); //read response
      int pingTime = (int)(System.currentTimeMillis() - sendTime);

      Gson gson = new Gson();
      Status st = gson.fromJson(json, Status.class);
      st.setPing(pingTime);
      return st;
  }

  private static byte[] getHandshakeAndEmptyLoginMessage(String strHostName, int intPort) throws IOException {
    byte [] handshakeMessage = getHandshakeMessage(strHostName, intPort);
    byte [] emptyLoginMessage = getEmptyLoginMessage();
    int len = handshakeMessage.length + emptyLoginMessage.length;
    byte[] byteMsg = new byte[len];
    System.arraycopy(handshakeMessage, 0, byteMsg, 0, handshakeMessage.length);
    System.arraycopy(emptyLoginMessage, 0, byteMsg, handshakeMessage.length, emptyLoginMessage.length);
    return byteMsg;
  }

  private static byte[] getHandshakeMessage(String strHostName, int intPort) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    DataOutputStream handshake = new DataOutputStream(buffer);
    byte [] handshakeMessage = createHandshakeMessage(strHostName, intPort);
    writeVarInt(handshake, handshakeMessage.length);  //prepend size
    handshake.write(handshakeMessage);
    return buffer.toByteArray();
  }

  private static byte [] createHandshakeMessage(String host, int port) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    DataOutputStream handshake = new DataOutputStream(buffer);
    byte [] bytes = host.getBytes(StandardCharsets.UTF_8);
    handshake.writeByte(0x00);             // packet id for handshake
    writeVarInt(handshake, 4);             // protocol version
    writeVarInt(handshake, bytes.length);  // host length
    handshake.write(bytes);                // host string
    handshake.writeShort(port);            // port
    writeVarInt(handshake, 1);             // state (1 for status, 2 for login)
    return buffer.toByteArray();
  }

  private static byte [] getEmptyLoginMessage() throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(buffer);
    out.writeByte(0x01); // size is only 1(length of packet id)
    out.writeByte(0x00); // packet id for ping
    return buffer.toByteArray();
  }

  private static byte [] getPingMessage(Long now) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(buffer);
    out.writeByte(0x09); // size is only 1(length of packet id)
    out.writeByte(0x01); // packet id for ping
    out.writeLong(now); //time!?
    return buffer.toByteArray();
  }

  private static int readVarInt(DataInputStream in)  throws IOException {
    int numRead = 0;
    int result = 0;
    byte read;
    do {
      read = in.readByte();
      int value = (read & 0b01111111);
      result |= (value << (7 * numRead));
      numRead++;
      if (numRead > 5) {
        throw new RuntimeException("VarInt is too big");
      }
    } while ((read & 0b10000000) != 0);
    return result;
  }

  private static void writeVarInt(DataOutputStream out, int value) throws IOException {
    do {
      byte temp = (byte)(value & 0b01111111);
      // Note: >>> means that the sign bit is shifted with the rest of the number rather than being left alone
      value >>>= 7;
      if (value != 0) {
        temp |= 0b10000000;
      }
      out.writeByte(temp);
    } while (value != 0);
  }
}
