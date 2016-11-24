package edu.cmu.cs.gabriel;

import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;
import com.google.gson.Gson;
import edu.cmu.cs.gabriel.network.AccStreamingThread;
import edu.cmu.cs.gabriel.network.NetworkProtocol;
import edu.cmu.cs.gabriel.network.ResultReceivingThread;
import edu.cmu.cs.gabriel.network.VideoStreamingThread;
import edu.cmu.cs.gabriel.token.ReceivedPacketInfo;
import edu.cmu.cs.gabriel.token.TokenController;
import java.io.File;

public class GabrielClientActivity extends BaseVoiceCommandActivity implements SensorEventListener {

  private static final String LOG_TAG = "Main";

  // major components for streaming sensor data and receiving information
  private VideoStreamingThread videoStreamingThread = null;
  private AccStreamingThread accStreamingThread = null;
  private ResultReceivingThread resultThread = null;
  private TokenController tokenController = null;

  private boolean isRunning = false;
  private CameraPreview preview = null;

  private SensorManager sensorManager = null;
  private Sensor sensorAcc = null;

  private ReceivedPacketInfo receivedPacketInfo = null;
  private ImageView receivedImg;
  private Gson mGson = new Gson();

  @Override protected void onCreate(Bundle savedInstanceState) {
    Log.v(LOG_TAG, "++onCreate");
    super.onCreate(savedInstanceState);
    receivedImg = (ImageView) findViewById(R.id.guidance_image);
  }

  @Override protected void onResume() {
    Log.v(LOG_TAG, "++onResume");
    super.onResume();
    initOnce();
    initPerRun(Const.SERVER_IP, Const.TOKEN_SIZE, null);
  }

  @Override protected void onPause() {
    Log.v(LOG_TAG, "++onPause");
    this.terminate();
    super.onPause();
  }

  @Override protected void onDestroy() {
    Log.v(LOG_TAG, "++onDestroy");
    super.onDestroy();
  }

  /**
   * Does initialization for the entire application. Called only once even for multiple
   * experiments.
   */
  private void initOnce() {
    Log.v(LOG_TAG, "++initOnce");
    preview = (CameraPreview) findViewById(R.id.camera_preview);
    preview.checkCamera();
    preview.setPreviewCallback(previewCallback);

    Const.ROOT_DIR.mkdirs();
    Const.EXP_DIR.mkdirs();

    // IMU sensors
    if (sensorManager == null) {
      sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
      sensorAcc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
      sensorManager.registerListener(this, sensorAcc, SensorManager.SENSOR_DELAY_NORMAL);
    }

    //voice and state machine
    StateMachine.getInstance().registerStateChangeCallback(new StateMachine.StateChangeCallback() {
      @Override public void onChange(int prevState, int currentState) {
        speechHelper.speech(StateMachine.getInstance().getVoiceInstruction(currentState));
      }
    });

    isRunning = true;
  }

  /**
   * Does initialization before each run (connecting to a specific server).
   * Called once before each experiment.
   */
  private void initPerRun(String serverIP, int tokenSize, File latencyFile) {
    Log.v(LOG_TAG, "++initPerRun");
    if (tokenController != null) {
      tokenController.close();
    }
    if ((videoStreamingThread != null) && (videoStreamingThread.isAlive())) {
      videoStreamingThread.stopStreaming();
      videoStreamingThread = null;
    }
    if ((accStreamingThread != null) && (accStreamingThread.isAlive())) {
      accStreamingThread.stopStreaming();
      accStreamingThread = null;
    }
    if ((resultThread != null) && (resultThread.isAlive())) {
      resultThread.close();
      resultThread = null;
    }

    tokenController = new TokenController(tokenSize, latencyFile);
    resultThread =
        new ResultReceivingThread(serverIP, Const.RESULT_RECEIVING_PORT, returnMsgHandler, this);
    resultThread.start();

    videoStreamingThread =
        new VideoStreamingThread(serverIP, Const.VIDEO_STREAM_PORT, returnMsgHandler,
            tokenController, this);
    videoStreamingThread.start();
  }

  private PreviewCallback previewCallback = new PreviewCallback() {
    // called whenever a new frame is captured
    public void onPreviewFrame(byte[] frame, Camera mCamera) {
      if (isRunning) {
        Camera.Parameters parameters = mCamera.getParameters();
        if (videoStreamingThread != null) {
          videoStreamingThread.push(frame, parameters);
        }
      }
    }
  };

  /**
   * Notifies token controller that some response is back
   */
  private void notifyToken() {
    Message msg = Message.obtain();
    msg.what = NetworkProtocol.NETWORK_RET_TOKEN;
    receivedPacketInfo.setGuidanceDoneTime(System.currentTimeMillis());
    msg.obj = receivedPacketInfo;
    try {
      tokenController.tokenHandler.sendMessage(msg);
    } catch (NullPointerException e) {
      // might happen because token controller might have been terminated
    }
  }

  /**
   * Handles messages passed from streaming threads and result receiving threads.
   */
  private Handler returnMsgHandler = new Handler() {
    public void handleMessage(Message msg) {
      if (msg.what == NetworkProtocol.NETWORK_RET_FAILED) {
        terminate();
      }
      if (msg.what == NetworkProtocol.NETWORK_RET_MESSAGE) {
        receivedPacketInfo = (ReceivedPacketInfo) msg.obj;
        receivedPacketInfo.setMsgRecvTime(System.currentTimeMillis());
      }
      if (msg.what == NetworkProtocol.NETWORK_RET_SPEECH) {
        String ttsMessage = (String) msg.obj;
        speechHelper.speech(ttsMessage);
      }
      if (msg.what == NetworkProtocol.NETWORK_RET_IMAGE
          || msg.what == NetworkProtocol.NETWORK_RET_ANIMATION) {
        Bitmap feedbackImg = (Bitmap) msg.obj;
        receivedImg.setImageBitmap(feedbackImg);
      }
      if (msg.what == NetworkProtocol.NETWORK_RET_AED_STATE) {
        String stageString = (String) msg.obj;
        StateMachine.StateModel model = mGson.fromJson(stageString, StateMachine.StateModel.class);
        StateMachine.getInstance().updateState(model.aed_state);
      }
      //if (msg.what == NetworkProtocol.NETWORK_RET_DETECTION) {
      //  ResultReceivingThread.DetectionHolder holder =
      //      (ResultReceivingThread.DetectionHolder) (msg.obj);
      //  FeatureDetectionModel[] models = holder.featureDetectionModels;
      //  Paint paint = new Paint();
      //  paint.setStyle(Paint.Style.STROKE);
      //  if (holder.bitmap != null) {
      //    Bitmap bitmap = holder.bitmap.copy(Bitmap.Config.RGB_565, true);
      //    Canvas canvas = new Canvas(bitmap);
      //    paint.setColor(Color.RED);
      //    paint.setStrokeWidth(3);
      //    paint.setTextSize(30);
      //    for (FeatureDetectionModel model : models) {
      //      Rect rect = model.getRect();
      //      canvas.drawRect(rect, paint);
      //      paint.setStrokeWidth(2);
      //      canvas.drawText(model.feature, rect.centerX(), rect.centerY(), paint);
      //      canvas.drawText(model.confidence + "", rect.centerX(), rect.centerY() + 40, paint);
      //    }
      //    canvas.save();
      //    receivedImg.setImageBitmap(bitmap);
      //  }
      //}
      if (msg.what == NetworkProtocol.NETWORK_RET_DONE) {
        notifyToken();
      }
    }
  };

  /**
   * Terminates all services.
   */
  private void terminate() {
    Log.v(LOG_TAG, "++terminate");

    isRunning = false;

    if ((resultThread != null) && (resultThread.isAlive())) {
      resultThread.close();
      resultThread = null;
    }
    if ((videoStreamingThread != null) && (videoStreamingThread.isAlive())) {
      videoStreamingThread.stopStreaming();
      videoStreamingThread = null;
    }
    if ((accStreamingThread != null) && (accStreamingThread.isAlive())) {
      accStreamingThread.stopStreaming();
      accStreamingThread = null;
    }
    if (tokenController != null) {
      tokenController.close();
      tokenController = null;
    }

    speechHelper.stop();

    if (preview != null) {
      preview.setPreviewCallback(null);
      preview.close();
      preview = null;
    }
    if (sensorManager != null) {
      sensorManager.unregisterListener(this);
      sensorManager = null;
      sensorAcc = null;
    }
  }

  /**************** SensorEventListener ***********************/
  // TODO: test accelerometer streaming
  @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
  }

  @Override public void onSensorChanged(SensorEvent event) {
    if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
    if (accStreamingThread != null) {
      //          accStreamingThread.push(event.values);
    }
    // Log.d(LOG_TAG, "acc_x : " + mSensorX + "\tacc_y : " + mSensorY);
  }
  /**************** End of SensorEventListener ****************/

}
