package edu.cmu.cs.gabriel;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

import edu.cmu.cs.gabriel.network.NetworkProtocol;
import edu.cmu.cs.gabriel.network.ResultReceivingThread;
import edu.cmu.cs.gabriel.network.VideoStreamingThread;
import edu.cmu.cs.gabriel.token.ReceivedPacketInfo;
import edu.cmu.cs.gabriel.token.TokenController;

/**
 * Created by liuhaodong1 on 3/13/17.
 *
 * For AED detection stage:
 * 1.find the AED device returning with AED Boxes (need to press start button)
 * 2.find the AED device returning with AED Boxes (need to plug yellow connector)
 * 3.find the AED device returning with AED Boxes (need to press the orange button)
 *
 *
 *
 */
public class GabrielClientSimpleActivity extends BaseVoiceCommandActivity{

    private static final String TAG = "Main";

    // major components for streaming sensor data and receiving information
    private VideoStreamingThread videoStreamingThread = null;
    private ResultReceivingThread resultThread = null;
    private TokenController tokenController = null;

    private boolean isRunning = false;
    private CameraPreview preview = null;

    /** For small window residing in the bottom**/
    private ImageView mWindow = null;
    private boolean isWindowPause = false;
    private int windowCounter = 0;
    private int windowSampling = 20;

    private ReceivedPacketInfo receivedPacketInfo = null;
    private Gson mGson = new Gson();

    private Vibrator vibrator;

    private Button mYes;
    private Button mNo;
    private RelativeLayout mHiddenInfoPanel;
    private TextView mHiddenState;
    private TextView mHiddenTimeout;
    private TextView mHiddenWrongPad;
    private TextView mHiddenWrongLeftPad;
    private TextView mHiddenAdultPAD;
    private TextView mHiddenPadDetect;
    private TextView mHiddenPatientAdult;
    private TextView mAEDBoxState;
    private int noCounter = 0;
    private int yesCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_simple);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED +
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON +
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        initHiddenInfoPanel();
        mWindow = (ImageView)findViewById(R.id.camera_window);
        yesCounter = 0;
        noCounter = 0;
        initWidget();
    }

    private void initHiddenInfoPanel(){
        mHiddenInfoPanel = (RelativeLayout) findViewById(R.id.main_hiden_info);
        mHiddenState = (TextView) findViewById(R.id.hidden_state);
        mHiddenWrongPad = (TextView)findViewById(R.id.hidden_pad_wrong);
        mHiddenAdultPAD = (TextView)findViewById(R.id.hidden_pad_adult);
        mHiddenPadDetect = (TextView)findViewById(R.id.hidden_pad_detect);
        mHiddenPatientAdult = (TextView)findViewById(R.id.hidden_patient_adult);
        mHiddenWrongLeftPad = (TextView)findViewById(R.id.hidden_pad_wrong_left);
        mAEDBoxState = (TextView)findViewById(R.id.hidden_frame_objects);
        mYes = (Button) findViewById(R.id.main_yes);
        mNo = (Button)findViewById(R.id.main_no);
    }

    private void initWidget() {
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        mYes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int leave = yesCounter % 4;
                if (leave == 0){
                    NetworkProtocol.USER_RESPONSE = StateMachine.RESP_AGE_DETECT_YES;
                } else if (leave == 1) {
                    NetworkProtocol.USER_RESPONSE = StateMachine.RESP_PEEL_PAD_YES;
                } else if (leave == 2) {
                    NetworkProtocol.USER_RESPONSE = StateMachine.RESP_LEFT_PAD_FINISHED;
                }else if(leave == 3){
                    NetworkProtocol.USER_RESPONSE = StateMachine.RESP_RIGHT_PAD_FINISHED;
                }
                Toast.makeText(getApplicationContext(),StateMachine.getRespStrByNum(NetworkProtocol.USER_RESPONSE),
                        Toast.LENGTH_SHORT).show();
                yesCounter++;
            }
        });
        mNo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int leave = noCounter % 4;
                NetworkProtocol.USER_RESPONSE = StateMachine.RESP_AGE_DETECT_NO;
                Toast.makeText(getApplicationContext(),StateMachine.getRespStrByNum(NetworkProtocol.USER_RESPONSE),
                        Toast.LENGTH_SHORT).show();
                noCounter++;
            }
        });
    }

    @Subscribe
    public void onStateUpdate(StateMachine.StateUpdateEvent event) {

        boolean isAEDFind = false;
        boolean isOrangeButtonFind = false;
        boolean isPlugFind = false;
        boolean isOrangeFlashFind = false;
        boolean isJointsFind = false;

        for (StateMachine.StateUpdateEvent.Field field : event.updateField) {
            StateMachine.StateModel model = event.currentModel;
            switch (field) {
                case AED_STATE: {
                    int currentState = model.aed_state;
                    mHiddenState.setText(String.valueOf(StateMachine.getStateStrByNum(currentState)));
                    speechHelper.playInstructionSound(currentState);
                }
                case TIMEOUT_STATE: {
                    int currentState = model.timeout_state;
                    Log.e(TAG,"timeout_state "+currentState);
                    speechHelper.playTimeoutSound(currentState);
                }
                case FRAME_AED:
                    isAEDFind = model.frame_aed;
                case FRAME_ORANGE_BTN:
                    isOrangeButtonFind = model.frame_orange_btn;
                case FRAME_ORANGE_FLASH:
                    isOrangeButtonFind = model.frame_orange_flash;
                case FRAME_YELLOW_PLUG:
                    isPlugFind = model.frame_yellow_plug;
                case FRAME_JOINTS:
                    isJointsFind = model.frame_joints;
                case PAD_Adult:
                    mHiddenAdultPAD.setText(String.valueOf(model.pad_adult));
                case PAD_WRONG_PAD:
                    mHiddenWrongPad.setText(String.valueOf(model.pad_wrong_pad));
                case PAD_DETECT:
                    String tmp = model.pad_detect[0] + " "+model.pad_detect[1];
                    mHiddenPadDetect.setText(tmp);
                case PAD_WRONG_LEFT:
                    mHiddenWrongLeftPad.setText(String.valueOf(model.pad_wrong_left));
                case PAITIENT_IS_ADULT:
                    mHiddenPatientAdult.setText(String.valueOf(model.patient_is_adult));
            }
        }
        mAEDBoxState.setText(String.valueOf(isAEDFind)+"/"+String.valueOf(isOrangeButtonFind)+"/"
        +String.valueOf(isPlugFind)+"/"+String.valueOf(isOrangeFlashFind));
    }

    public Rect getRect(List<Float> area) {
        float left = area.get(0);
        float top = area.get(1);
        float right = area.get(2);
        float bottom = area.get(3);
        return new Rect((int) left, (int) top, (int) right, (int) bottom);
    }

    @Override
    protected void onResume() {
        Log.v(TAG, "++onResume");
        super.onResume();
        EventBus.getDefault().register(this);
        initOnce();
        initPerRun(Const.SERVER_IP, Const.TOKEN_SIZE, null);
    }

    @Override
    protected void onPause() {
        EventBus.getDefault().unregister(this);
        this.terminate();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.v(TAG, "++onDestroy");
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

    private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        // called whenever a new frame is captured
        public void onPreviewFrame(byte[] frame, Camera mCamera) {
            if (isRunning) {
                Camera.Parameters parameters = mCamera.getParameters();
                if (videoStreamingThread != null) {
                    videoStreamingThread.push(frame, parameters);
//                    if (!isWindowPause){
//                        if(mWindow != null && windowCounter > windowSampling){
//
//                            int width = parameters.getPreviewSize().width;
//                            int height = parameters.getPreviewSize().height;
//
//                            ByteArrayOutputStream outstr = new ByteArrayOutputStream();
//                            Rect rect = new Rect(0, 0, width, height);
//                            YuvImage yuvimage=new YuvImage(frame, ImageFormat.NV21,width,height,null);
//                            yuvimage.compressToJpeg(rect, 100, outstr);
//                            Bitmap bmp = BitmapFactory.decodeByteArray(outstr.toByteArray(), 0, outstr.size());
//                            mWindow.setImageBitmap(bmp);
//                            windowCounter = 0;
//                        }else{
//                            windowCounter ++;
//                        }
//                    }
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
                Log.e(TAG,"image data received height "+feedbackImg.getHeight()+" width "+feedbackImg.getWidth());
                mWindow.setImageBitmap(feedbackImg);
            }
            if (msg.what == NetworkProtocol.NETWORK_RET_AED_STATE) {
                StateMachine.StateModel model = (StateMachine.StateModel) msg.obj;
                StateMachine.getInstance().updateState(model);
            }
            if (msg.what == NetworkProtocol.NETWORK_RET_DONE) {
                notifyToken();
            }
        }
    };

    /**
     * Terminates all services.
     */
    private void terminate() {

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
