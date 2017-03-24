package edu.cmu.cs.gabriel.network;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import com.google.gson.Gson;
import edu.cmu.cs.gabriel.GlobalCache;
import edu.cmu.cs.gabriel.StateMachine;
import edu.cmu.cs.gabriel.model.FeatureDetectionModel;
import edu.cmu.cs.gabriel.token.ReceivedPacketInfo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ResultReceivingThread extends Thread {

  private static final String LOG_TAG = "ResultThread";

  private boolean isRunning = false;

  // TCP connection
  private InetAddress remoteIP;
  private int remotePort;
  private Socket tcpSocket;
  private DataOutputStream networkWriter;
  private DataInputStream networkReader;

  private Handler returnMsgHandler;

  // animation
  private Timer timer = null;
  private Bitmap[] animationFrames = new Bitmap[2];
  private int[] animationPeriods = new int[2]; // how long each frame is shown, in millisecond
  private int animationDisplayIdx = -1;
  private int nAnimationFrames = -1;

  private Gson mGson;
  private Context context;

  public static class DetectionHolder {
    public Bitmap bitmap;
    public FeatureDetectionModel[] featureDetectionModels;
  }

  public ResultReceivingThread(String serverIP, int port, Handler returnMsgHandler,
      Context context) {
    this.context = context;
    isRunning = false;
    this.returnMsgHandler = returnMsgHandler;
    try {
      remoteIP = InetAddress.getByName(serverIP);
    } catch (UnknownHostException e) {
      Log.e(LOG_TAG, "unknown host: " + e.getMessage());
    }
    remotePort = port;
    mGson = new Gson();
  }

  @Override public void run() {
    this.isRunning = true;
    Log.i(LOG_TAG, "Result receiving thread running");

    try {
      tcpSocket = new Socket();
      tcpSocket.setTcpNoDelay(true);
      tcpSocket.connect(new InetSocketAddress(remoteIP, remotePort), 5 * 1000);
      networkWriter = new DataOutputStream(tcpSocket.getOutputStream());
      networkReader = new DataInputStream(tcpSocket.getInputStream());
    } catch (IOException e) {
      Log.e(LOG_TAG, "Error in initializing Data socket: " + e);
      this.notifyError(e.getMessage());
      this.isRunning = false;
      return;
    }

    while (isRunning) {
      try {
        String recvMsg = this.receiveMsg(networkReader);
        this.notifyReceivedData(recvMsg);
      } catch (IOException e) {
        Log.w(LOG_TAG, "Error in receiving result, maybe because the app has paused");
        this.notifyError(e.getMessage());
        break;
      }
    }
  }

  /**
   * @return a String representing the received message from @reader
   */
  private String receiveMsg(DataInputStream reader) throws IOException {
    int retLength = reader.readInt();
    byte[] recvByte = receiveNBytes(reader, retLength);
    String receivedString = new String(recvByte);
    return receivedString;
  }

  private byte[] receiveNBytes(DataInputStream reader, int retLength) throws IOException {
    byte[] recvByte = new byte[retLength];
    int readSize = 0;
    while (readSize < retLength) {
      int ret = reader.read(recvByte, readSize, retLength - readSize);
      if (ret <= 0) {
        break;
      }
      readSize += ret;
    }
    return recvByte;
  }

  private byte[] parseReceivedDataByType(JSONObject header, byte[] data, String type)
      throws JSONException {
    if (header.has(type)) {
      JSONArray dataInfo = header.getJSONArray(type);
      int dataOffset = dataInfo.getInt(0);
      int dataSize = dataInfo.getInt(1);
      //            Log.d(LOG_TAG, "offset: " + dataOffset + " size: " + dataSize);
      byte[] ret = Arrays.copyOfRange(data, dataOffset, dataOffset + dataSize);
      return ret;
    }
    return null;
  }

  private void notifyReceivedData(String recvData) {
    // convert the message to JSON
    String result = null;
    long frameID = -1;
    String engineID = "";
    String status = "";
    int injectedToken = 0;
    int dataSize = -1;
    Bitmap imageFeedback = null;

    try {
      JSONObject recvJSON = new JSONObject(recvData);
      frameID = recvJSON.getLong(NetworkProtocol.HEADER_MESSAGE_FRAME_ID);
      engineID = recvJSON.getString(NetworkProtocol.HEADER_MESSAGE_ENGINE_ID);
      status = recvJSON.getString(NetworkProtocol.HEADER_MESSAGE_STATUS);
      dataSize = recvJSON.getInt(NetworkProtocol.HEADER_MESSAGE_DATA_SIZE);

      Message msg = Message.obtain();
      msg.what = NetworkProtocol.NETWORK_RET_MESSAGE;
      msg.obj = new ReceivedPacketInfo(frameID, engineID, status);
      this.returnMsgHandler.sendMessage(msg);

      byte[] data = receiveNBytes(networkReader, dataSize);
      // image guidance
      byte[] imageData = parseReceivedDataByType(recvJSON, data, NetworkProtocol.HEADER_MESSAGE_IMAGE);
      if (imageData != null) {
        Log.e("imageData","received "+imageData.length);
        imageFeedback = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
//        ByteArrayOutputStream outstr = new ByteArrayOutputStream();
//        Rect rect = new Rect(0, 0, 120, 120);
//        YuvImage yuvimage=new YuvImage(imageData, ImageFormat.NV21,120,120,null);
//        yuvimage.compressToJpeg(rect, 100, outstr);
//        Bitmap bmp = BitmapFactory.decodeByteArray(outstr.toByteArray(), 0, outstr.size());
        msg = Message.obtain();
        msg.what = NetworkProtocol.NETWORK_RET_IMAGE;
//        msg.obj = bmp;
        msg.obj = imageFeedback;
        this.returnMsgHandler.sendMessage(msg);
      }

      // animation guidance
      byte[] animData =
          parseReceivedDataByType(recvJSON, data, NetworkProtocol.HEADER_MESSAGE_ANIMATION);
      if (animData != null) {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(animData));
        nAnimationFrames = dis.readInt();
        for (int i = 0; i < nAnimationFrames; i++) {
          int frameSize = dis.readInt();
          byte[] frameData = new byte[frameSize];
          int numBytes = dis.read(frameData);
          if (numBytes != frameSize) {
            Log.d(LOG_TAG, "failed to read in the whole image");
          }
          animationFrames[i] = BitmapFactory.decodeByteArray(frameData, 0, frameData.length);
          //length current is arbitrary
          animationPeriods[i] = 100;
        }
        animationDisplayIdx = -1;
        if (timer == null) {
          timer = new Timer();
          timer.schedule(new animationTask(), 0);
        }
      }

      byte[] speechData =
          parseReceivedDataByType(recvJSON, data, NetworkProtocol.HEADER_MESSAGE_SPEECH);
      if (speechData != null) {
        String speechFeedback = new String(speechData);
        msg = Message.obtain();
        msg.what = NetworkProtocol.NETWORK_RET_SPEECH;
        msg.obj = speechFeedback;
        Log.d(LOG_TAG, "speech guidance: " + speechFeedback);
        //uncomment to let tts speek
        this.returnMsgHandler.sendMessage(msg);
      }

      byte[] aedStateData =
          parseReceivedDataByType(recvJSON, data, NetworkProtocol.HEADER_MESSAGE_AED_STATE);
      if (aedStateData != null) {
        String aedStateString = new String(aedStateData);
        //Log.e("REC aedStateString",aedStateString);
        StateMachine.StateModel model =
            mGson.fromJson(aedStateString, StateMachine.StateModel.class);
        model.frameId = frameID;

        msg = Message.obtain();
        msg.what = NetworkProtocol.NETWORK_RET_AED_STATE;
        msg.obj = model;
        //uncomment to let tts speek
        this.returnMsgHandler.sendMessage(msg);
      }

      // animation guidance
      byte[] detectionData =
          parseReceivedDataByType(recvJSON, data, NetworkProtocol.HEADER_MESSAGE_DETECTION);
      if (detectionData != null) {
        String detectionString = new String(detectionData);
        FeatureDetectionModel[] array =
            mGson.fromJson(detectionString, FeatureDetectionModel[].class);

        msg = Message.obtain();
        msg.what = NetworkProtocol.NETWORK_RET_DETECTION;
        DetectionHolder holder = new DetectionHolder();
        GlobalCache.FrameHolder frameHolder =
            GlobalCache.getInstance(context).getFrameById(frameID);
        if (frameHolder != null) {
          holder.bitmap =
              BitmapFactory.decodeByteArray(frameHolder.data, 0, frameHolder.data.length);
          GlobalCache.getInstance(context).clearOldItems(frameID);
        }
        holder.featureDetectionModels = array;
        msg.obj = holder;
        //uncomment to let tts speek
        this.returnMsgHandler.sendMessage(msg);
      }
    } catch (JSONException e) {
      Log.e(LOG_TAG, "returned json parsed incorrectly!");
      e.printStackTrace();
    } catch (IOException e) {
      Log.e(LOG_TAG, "received packet data error");
      e.printStackTrace();
    } finally {
      // done processing return message
      Message msg = Message.obtain();
      msg.what = NetworkProtocol.NETWORK_RET_DONE;
      this.returnMsgHandler.sendMessage(msg);
    }
  }

  private class animationTask extends TimerTask {
    @Override public void run() {
      Log.v(LOG_TAG, "Running timer task");
      animationDisplayIdx = (animationDisplayIdx + 1) % nAnimationFrames;
      Message msg = Message.obtain();
      msg.what = NetworkProtocol.NETWORK_RET_ANIMATION;
      msg.obj = animationFrames[animationDisplayIdx];
      returnMsgHandler.sendMessage(msg);
      timer.schedule(new animationTask(), animationPeriods[animationDisplayIdx]);
    }
  }

  ;

  public void close() {
    this.isRunning = false;

    try {
      if (this.networkReader != null) {
        this.networkReader.close();
        this.networkReader = null;
      }
    } catch (IOException e) {
    }
    try {
      if (this.networkWriter != null) {
        this.networkWriter.close();
        this.networkWriter = null;
      }
    } catch (IOException e) {
    }
    try {
      if (this.tcpSocket != null) {
        this.tcpSocket.shutdownInput();
        this.tcpSocket.shutdownOutput();
        this.tcpSocket.close();
        this.tcpSocket = null;
      }
    } catch (IOException e) {
    }
  }

  /**
   * Notifies error to the main thread
   */
  private void notifyError(String errorMessage) {
    Message msg = Message.obtain();
    msg.what = NetworkProtocol.NETWORK_RET_FAILED;
    msg.obj = errorMessage;
    this.returnMsgHandler.sendMessage(msg);
  }
}
