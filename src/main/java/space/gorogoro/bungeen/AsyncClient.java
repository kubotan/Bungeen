package space.gorogoro.bungeen;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AsyncClient {
    private static final int BUFFER_SIZE = 1 * 1024 * 1024;
    private long wait;
    private boolean waitFlag = true;
    private int timeout;
    private AsynchronousSocketChannel client;
    private Future<Void> future;
    
    public AsyncClient(String hostName, int port) throws NoRouteToHostException, InterruptedException, ExecutionException {
        try {
        		this.wait = 200;
        		this.timeout = 3;
            client = AsynchronousSocketChannel.open();
            InetSocketAddress hostAddress = new InetSocketAddress(hostName, port);
            future = client.connect(hostAddress);
            start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setTimeout(int timeout) {
    		this.timeout = timeout;
    }

    public void setWait(long wait) {
		this.wait = wait;
    }

    public void start() throws NoRouteToHostException, InterruptedException, ExecutionException {
        	future.get();
    }

    public void setWaitFlag(boolean b) {
    		this.waitFlag = b;
    }
    
    public byte [] sendMessage(byte [] byteMsg) throws InterruptedException, ExecutionException, TimeoutException{
      ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
      buffer.clear();
      buffer.put(byteMsg);
      buffer.flip();
      client.write(buffer).get(timeout, TimeUnit.SECONDS);
      if(waitFlag) {
      		Thread.sleep(wait);
      }
      buffer.clear();
      client.read(buffer).get(timeout, TimeUnit.SECONDS);
      buffer.flip();
      byte[] bytes = new byte[buffer.limit()];
      buffer.get(bytes);
      buffer.compact();
      return bytes;
    }
    
    public void stop() {
        try {
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}