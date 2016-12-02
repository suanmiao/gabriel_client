package edu.cmu.cs.gabriel.network;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import edu.cmu.cs.gabriel.GlobalCache;
import edu.cmu.cs.gabriel.token.TokenController;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class VideoStreamingThread extends Thread {

  private static final String LOG_TAG = "VideoStreaming";

  private boolean isRunning = false;
  private boolean isPing = true;

  // TCP connection
  private InetAddress remoteIP;
  private int remotePort;
  private Socket tcpSocket = null;
  private DataOutputStream networkWriter = null;
  private DataInputStream networkReader = null;
  private VideoControlThread networkReceiver = null;

  // frame data shared between threads
  private long frameID = 0;
  private byte[] frameBuffer = null;
  private Object frameLock = new Object();

  private Handler networkHandler = null;
  private TokenController tokenController = null;
  private Context context;

  //test code
  private long prev_token = -1;

  public VideoStreamingThread(String serverIP, int port, Handler handler,
      TokenController tokenController, Context context) {
    this.context = context;
    isRunning = false;
    this.networkHandler = handler;
    this.tokenController = tokenController;

    try {
      remoteIP = InetAddress.getByName(serverIP);
    } catch (UnknownHostException e) {
      Log.e(LOG_TAG, "unknown host: " + e.getMessage());
    }
    remotePort = port;
  }

  public void run() {
    this.isRunning = true;
    Log.i(LOG_TAG, "Streaming thread running");

    // initialization of the TCP connection
    try {
      tcpSocket = new Socket();
      tcpSocket.setTcpNoDelay(true);
      tcpSocket.connect(new InetSocketAddress(remoteIP, remotePort), 5 * 1000);
      networkWriter = new DataOutputStream(tcpSocket.getOutputStream());
      networkReader = new DataInputStream(tcpSocket.getInputStream());
    } catch (IOException e) {
      Log.e(LOG_TAG, "Error in initializing network socket: " + e);
      this.notifyError(e.getMessage());
      this.isRunning = false;
      return;
    }

    while (this.isRunning) {
      try {
        ///test code
        if (prev_token != this.tokenController.getCurrentToken()) {
          //Log.e("suan token change ",
          //    "prev: " + prev_token + ", current token " + this.tokenController.getCurrentToken());
        }

        prev_token = this.tokenController.getCurrentToken();

        // check token
        if (this.tokenController.getCurrentToken() <= 0) {
          Log.e(LOG_TAG, "no token available");
          continue;
        }

       /*
        * The second part is to stream real data to the server.
        */
        // get data in the frame buffer
        byte[] data = null;
        long dataTime = 0;
        long compressedTime = 0;
        long sendingFrameID = 0;
        Log.e(LOG_TAG, "waiting for lock");
        synchronized (frameLock) {
          Log.e(LOG_TAG, "frame buffer might be null" + this.frameBuffer);
          while (this.frameBuffer == null) {
            try {
              frameLock.wait();
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }
          Log.e(LOG_TAG, "begin sending" + this.frameBuffer);
          data = this.frameBuffer;
          dataTime = System.currentTimeMillis();

          sendingFrameID = this.frameID;

          GlobalCache.getInstance(context).enqueue(sendingFrameID, data);
          Log.v(LOG_TAG, "sending:" + sendingFrameID);
          this.frameBuffer = null;
        }
        Log.v(LOG_TAG, "beging sending" + sendingFrameID);

        // make it as a single packet
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        byte[] header = ("{\""
            + NetworkProtocol.HEADER_MESSAGE_FRAME_ID
            + "\":"
            + sendingFrameID
            + "}").getBytes();
        dos.writeInt(header.length);
        dos.write(header);
        dos.writeInt(data.length);
        dos.write(data);

        // send packet and consume tokens
        this.tokenController.logSentPacket(sendingFrameID, dataTime, compressedTime);
        this.tokenController.decreaseToken();
        networkWriter.write(baos.toByteArray());
        networkWriter.flush();
        Log.v(LOG_TAG, "finish sending" + sendingFrameID);
      } catch (IOException e) {
        Log.e(LOG_TAG, "Error in sending packet: " + e);
        this.notifyError(e.getMessage());
        this.isRunning = false;
        return;
      }
    }
    this.isRunning = false;
    Log.e(LOG_TAG, "finish streaming thread");
  }

  /**
   * Called whenever a new frame is generated
   * Puts the new frame into the @frameBuffer
   */
  public void push(byte[] frame, Parameters parameters) {
    synchronized (frameLock) {
      Log.d("frame", "compress");
      Size cameraImageSize = parameters.getPreviewSize();
      YuvImage image = new YuvImage(frame, parameters.getPreviewFormat(), cameraImageSize.width,
          cameraImageSize.height, null);
      ByteArrayOutputStream tmpBuffer = new ByteArrayOutputStream();
      // chooses quality 67 and it roughly matches quality 5 in avconv
      //image.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 67, tmpBuffer);
      image.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 67, tmpBuffer);
      this.frameBuffer = tmpBuffer.toByteArray();
      this.frameID++;
      frameLock.notify();
    }
  }

  public void stopStreaming() {
    isRunning = false;
    if (tcpSocket != null) {
      try {
        tcpSocket.close();
      } catch (IOException e) {
      }
    }
    if (networkWriter != null) {
      try {
        networkWriter.close();
      } catch (IOException e) {
      }
    }
    if (networkReceiver != null) {
      networkReceiver.close();
    }
  }

  /**
   * Notifies error to the main thread
   */
  private void notifyError(String message) {
    // callback
    Message msg = Message.obtain();
    msg.what = NetworkProtocol.NETWORK_RET_FAILED;
    Bundle data = new Bundle();
    data.putString("message", message);
    msg.setData(data);
    this.networkHandler.sendMessage(msg);
  }
}
