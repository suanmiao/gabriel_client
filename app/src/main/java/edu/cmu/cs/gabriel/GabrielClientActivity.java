package edu.cmu.cs.gabriel;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import com.google.gson.Gson;
import edu.cmu.cs.gabriel.network.NetworkProtocol;
import edu.cmu.cs.gabriel.network.ResultReceivingThread;
import edu.cmu.cs.gabriel.network.VideoStreamingThread;
import edu.cmu.cs.gabriel.token.ReceivedPacketInfo;
import edu.cmu.cs.gabriel.token.TokenController;
import java.io.File;
import java.util.List;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

public class GabrielClientActivity extends BaseVoiceCommandActivity {

  private static final String LOG_TAG = "Main";

  // major components for streaming sensor data and receiving information
  private VideoStreamingThread videoStreamingThread = null;
  private ResultReceivingThread resultThread = null;
  private TokenController tokenController = null;

  private boolean isRunning = false;
  private CameraPreview preview = null;

  private ReceivedPacketInfo receivedPacketInfo = null;
  private Gson mGson = new Gson();
  private ListView listMain;
  private TextView textOverall;
  private TextView textDetail;
  private SListAdapter adapter;
  private int clickTime = 0;
  private long lastClickTime = 0;
  private Vibrator vibrator;
  private String configedIP = "";

  @Override protected void onCreate(Bundle savedInstanceState) {
    Log.v(LOG_TAG, "++onCreate");
    super.onCreate(savedInstanceState);
    initWidget();
    initData();
  }

  private void initWidget() {
    listMain = (ListView) findViewById(R.id.main_list);
    textOverall = (TextView) findViewById(R.id.main_text_overall_status);
    textDetail = (TextView) findViewById(R.id.main_text_detail_status);
    adapter = new SListAdapter(this);
    listMain.setAdapter(adapter);
    vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

    textOverall.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        vibrator.vibrate(30);
        long time = System.currentTimeMillis();
        if (time - lastClickTime < 1000) {
          clickTime++;
          if (clickTime == 5) {
            startActivity(new Intent(GabrielClientActivity.this, ConfigActivity.class));
            if (preview != null) {
              preview.setPreviewCallback(null);
              preview.close();
              preview = null;
            }
            terminate();
            finish();
            clickTime = 0;
          }
        }
        lastClickTime = time;
      }
    });
  }

  @Subscribe public void onStateUpdate(StateMachine.StateUpdateEvent event) {
    for (StateMachine.StateUpdateEvent.Field field : event.updateField) {
      switch (field) {
        case AED_STATE: {
          int currentState = event.currentModel.aed_state;
          Log.e("suan stage change ", "" + currentState);
          speechHelper.playInstructionSound(currentState);
          Util.bindOverallState(textOverall);
          Util.bindStateText(adapter, currentState);
          listMain.setSelection(adapter.getCount() - 1);
        }

        break;
        case TIMEOUT_STATE: {
          int currentState = event.currentModel.timeout_state;
          Log.e("suan timeout ", "" + currentState);
          speechHelper.playTimeoutSound(currentState);
          Util.bindOverallState(textOverall);
          Util.bindTimeoutText(adapter, currentState);
          listMain.setSelection(adapter.getCount() - 1);
        }
        break;
        case FRAME_AED:
          break;
        case FRAME_ORANGE_FLASH:
          break;
        case FRAME_YELLOW_PLUG:
          break;
      }
    }
    drawFeatures(event);
    Util.bindDetailState(textDetail);
  }

  public void drawFeatures(StateMachine.StateUpdateEvent event) {
    long frameId = event.currentModel.frameId;
    GlobalCache.FrameHolder holder = GlobalCache.getInstance(this).getFrameById(frameId);
    Log.e("SUAN", "aed box " + event.currentModel.frame_aed_box + " holder " + holder);
    if (holder != null) {
      Bitmap bitmap = BitmapFactory.decodeByteArray(holder.data, 0, holder.data.length);
      bitmap = bitmap.copy(Bitmap.Config.RGB_565, true);
      Paint paint = new Paint();
      paint.setStyle(Paint.Style.STROKE);
      Canvas canvas = new Canvas(bitmap);
      paint.setColor(Color.RED);
      paint.setStrokeWidth(3);
      paint.setTextSize(30);

      if (event.currentModel.frame_aed_box != null && event.currentModel.frame_aed_box.size() > 0) {
        Rect rect = getRect(event.currentModel.frame_aed_box);
        canvas.drawRect(rect, paint);
        paint.setStrokeWidth(2);
        canvas.drawText("aed", rect.centerX(), rect.centerY(), paint);
      }

      if (event.currentModel.frame_yellow_plug_box != null && event.currentModel.frame_yellow_plug_box.size() > 0) {
        Rect rect = getRect(event.currentModel.frame_yellow_plug_box);
        canvas.drawRect(rect, paint);
        paint.setStrokeWidth(2);
        canvas.drawText("YP", rect.centerX(), rect.centerY(), paint);
      }

      if (event.currentModel.frame_orange_flash_box != null && event.currentModel.frame_orange_flash_box.size() > 0) {
        Rect rect = getRect(event.currentModel.frame_orange_flash_box);
        canvas.drawRect(rect, paint);
        paint.setStrokeWidth(2);
        canvas.drawText("OF", rect.centerX(), rect.centerY(), paint);
      }

      canvas.save();
      listMain.setBackground(new BitmapDrawable(getResources(), bitmap));
    }
  }

  public Rect getRect(List<Float> area) {
    float left = area.get(0);
    float top = area.get(1);
    float right = area.get(2);
    float bottom = area.get(3);
    return new Rect((int) left, (int) top, (int) right, (int) bottom);
  }

  public void initData() {
    Intent intent = getIntent();
    if (intent != null && !TextUtils.isEmpty(intent.getStringExtra(ConfigActivity.KEY_IP))) {
      configedIP = intent.getStringExtra(ConfigActivity.KEY_IP);
    }

    Util.bindOverallState(textOverall);
    Util.bindDetailState(textDetail);
  }

  @Override protected void onResume() {
    Log.v(LOG_TAG, "++onResume");
    super.onResume();
    EventBus.getDefault().register(this);
    initOnce();
    initPerRun(Const.SERVER_IP, Const.TOKEN_SIZE, null);
//    initPerRun(TextUtils.isEmpty(configedIP) ? Const.SERVER_IP : configedIP, Const.TOKEN_SIZE,
//        null);
  }

  @Override protected void onPause() {
     EventBus.getDefault().unregister(this);
    this.terminate();
    super.onPause();
  }

  @Override protected void onDestroy() {
    Log.v(LOG_TAG, "++onDestroy");
    super.onDestroy();
    if (preview != null) {
      preview.setPreviewCallback(null);
      preview.close();
      preview = null;
    }
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
      //if (msg.what == NetworkProtocol.NETWORK_RET_FAILED) {
      //  terminate();
      //}
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
      }
      if (msg.what == NetworkProtocol.NETWORK_RET_AED_STATE) {
        StateMachine.StateModel model = (StateMachine.StateModel) msg.obj;
        StateMachine.getInstance().updateState(model);
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
    if (tokenController != null) {
      tokenController.close();
      tokenController = null;
    }

    speechHelper.stop();
  }
}
