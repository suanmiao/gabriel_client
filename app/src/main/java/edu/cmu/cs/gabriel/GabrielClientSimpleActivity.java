package edu.cmu.cs.gabriel;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    private static final String TAG = "GabrielClient";

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
    private TextView mJoints;
    private static final int client_none = -10000;
    int currentState = client_none;

    /** For speech to text **/
    private final int SPEECH_INPUT = 1234;
    private int respYes = 1;
    private int respNo = -1;
    private int respNoInit = 0;
    private int introLength = 32;
    private String userYes = "yes";
    private String userNo = "no";

    boolean is_voice_reco_running = false;

    public static final String TAG_CURRENT_STAGE = "CURRENT_STAGE";
    public static final String TAG_IS_ADULT = "TAG_IS_ADULT"; //0 ADULT, 1 CHILDREN
    public static final String TAG_IS_DEFIB = "TAG_IS_DEFIB"; //1 yes, -1 no
    ACache mCache;

    Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int stage = msg.what;
            boolean is_prompt = false;
            is_voice_reco_running = true;
            switch (stage){
                case StateMachine.PAD_PRE_1:
                    is_prompt = true;
                    break;
                case StateMachine.PAD_PRE_2:
                    is_prompt = true;
                    break;
                case StateMachine.PAD_AGE_CONFIRM:
                    is_prompt = true;
                    break;
                case StateMachine.PAD_NONE:
                    is_prompt = true;
                    break;
                case StateMachine.PAD_CORRECT_PAD:
                    is_prompt = true;
                    break;
                case StateMachine.PAD_DEFIB_CONFIRM:
                    is_prompt = true;
                    break;
                case StateMachine.PAD_PEEL_LEFT:
                    is_prompt = true;
                    break;
                case StateMachine.PAD_WAIT_LEFT_PAD:
                    is_prompt = true;
                    break;
                case StateMachine.PAD_PEEL_RIGHT:
                    is_prompt = true;
                    break;
                case StateMachine.PAD_WAIT_RIGHT_PAD:
                    is_prompt = true;
                    break;
                case StateMachine.PAD_FINISH:
                    is_prompt = true;
                    break;
            }
            if(is_prompt)
                promptSpeechInput();
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_simple);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED +
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON +
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        initHiddenInfoPanel();
        mCache = ACache.get(getApplicationContext());
        mWindow = (ImageView)findViewById(R.id.camera_window);
        speechHelper.setVoiceInput(mHandler);
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
        mJoints = (TextView)findViewById(R.id.hidden_pad_joints);
        mYes = (Button) findViewById(R.id.main_yes);
        mNo = (Button)findViewById(R.id.main_no);
    }

    private void initWidget() {
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        mYes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stateUserResponse(respYes);
            }
        });

        mNo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stateUserResponse(respNo);
            }
        });
    }

    /**
     * Showing google speech input dialog
     * */
    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra("CURRENT_STAGE",currentState);
        try {
            startActivityForResult(intent, SPEECH_INPUT);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG,"Voice recognition didn't go through");
        }
    };

    private void restoreSavedState(){
        String is_adult = mCache.getAsString(TAG_IS_ADULT);
        String is_defib = mCache.getAsString(TAG_IS_DEFIB);
        if(is_adult != null && is_adult.equals("0")){
            speechHelper.updateMapAdult(true);
        }else if(is_adult != null && is_adult.equals("1")){
            speechHelper.updateMapAdult(false);
        }
        if(is_defib != null && is_defib.equals("1")){
            speechHelper.updateMapDefib(true);
        }else if(is_defib != null && is_defib.equals("-1")){
            speechHelper.updateMapDefib(false);
        }
    }

    /**
     * Receiving speech input
     * */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        String stage = mCache.getAsString(TAG_CURRENT_STAGE);
        Log.e(TAG,"onActivityResult stage "+stage);
        if(stage != null && !stage.trim().equals("")){
            currentState = Integer.valueOf(stage);
        }

        switch (requestCode) {
            case SPEECH_INPUT: {
                Log.e(TAG, "Got speech data result");
                if (data != null) {
                } else {
                    Log.e(TAG, "No data");
                }
                if (resultCode == RESULT_OK && data != null) {
                    int yerOrNo = 0;

                    ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    for(String input : result){
                        Log.e(TAG,input);
                        input = input.toLowerCase();
                        if(input.startsWith("yes")){
                            yerOrNo = YES;
                            break;
                        }else if(input.startsWith("no")){
                            yerOrNo = NO;
                            break;
                        }
                    }
                    if (yerOrNo == 1) {
                        Log.e(TAG, "This YES was received");
                        is_voice_reco_running = false;
                        stateUserResponse(respYes);
                    } else if (yerOrNo == -1) {
                        Log.e(TAG, "This NO was received");
                        is_voice_reco_running = false;
                        stateUserResponse(respNo);
                    } else {
                        Log.e(TAG,"No resp was received");
                        Toast.makeText(getApplicationContext(),"Can't recognize the voice, wait to say it again.",
                                Toast.LENGTH_SHORT).show();
                        is_voice_reco_running = false;
                        mHandler.sendEmptyMessageDelayed(currentState, 5000);
                    }
                }
                break;
            }

        }
    }

    public static final int YES = 1;
    public static final int NO = -1;
    /*
     *  //1 yes, 0 not initialized, -1 No
     */
    private void stateUserResponse(int yesOrNo){

        int resp = -10000;
        int instr = -10000;
        if (!speechHelper.is_finished){
            Toast.makeText(getApplicationContext(),"wait until voice finished",Toast.LENGTH_SHORT).show();
            return;
        }

        Log.e(TAG, "stateUserResponse "+"resp = "+yesOrNo);
        Log.e(TAG, "stateUserResponse " + "current = "+currentState);

        switch (currentState){

            case client_none:
                break;

            case StateMachine.PAD_PRE_1:
                if(yesOrNo == YES) {
                    resp = StateMachine.RESP_PAD_PRE_1;
                    NetworkProtocol.USER_RESPONSE = resp;
                }
                break;

            case StateMachine.PAD_PRE_2:
                if(yesOrNo == YES) {
                    resp = StateMachine.RESP_PAD_PRE_2;
                    NetworkProtocol.USER_RESPONSE = resp;
                }
                break;

            case StateMachine.PAD_NONE:
                 resp = StateMachine.RESP_START_DETECTION;
                 NetworkProtocol.USER_RESPONSE = resp;
                 speechHelper.playInstructionSound(resp);
                 break;

            case StateMachine.PAD_AGE_CONFIRM:
                if(yesOrNo == respYes){
                    resp = StateMachine.RESP_AGE_DETECT_YES;
                    NetworkProtocol.USER_RESPONSE = resp;
                    Log.e("###", "Responding YES to server");
                }else if(yesOrNo == respNo){
                    resp = StateMachine.RESP_AGE_DETECT_NO;
                    NetworkProtocol.USER_RESPONSE = resp;
                    Log.e("###", "Responding NO to server");
                }
                break;

            case StateMachine.PAD_CORRECT_PAD:
                resp = StateMachine.RESP_PAD_CORRECT_PAD;
                NetworkProtocol.USER_RESPONSE = resp;
                break;

            case StateMachine.PAD_DEFIB_CONFIRM:
                if(yesOrNo == respYes){
                    speechHelper.updateMapDefib(true);
                    mCache.put(TAG_IS_DEFIB,"1");
                    resp = StateMachine.RESP_DEFIB_YES;
                    NetworkProtocol.USER_RESPONSE = resp;
                }else if(yesOrNo == respNo){
                    speechHelper.updateMapDefib(false);
                    mCache.put(TAG_IS_DEFIB, "-1");
                    resp = StateMachine.RESP_DEFIB_NO;
                    NetworkProtocol.USER_RESPONSE = resp;
                }
                instr = StateMachine.PAD_DEFIB_CONFIRM;
                speechHelper.playInstructionSound(instr);
                break;

            case StateMachine.PAD_PEEL_LEFT:
                // TORESOLVE
                resp = StateMachine.RESP_PEEL_PAD_LEFT; // There is no mapping for this instruction
                NetworkProtocol.USER_RESPONSE = resp;
                instr = StateMachine.PAD_PEEL_LEFT;
                speechHelper.playInstructionSound(instr);
                break;

            case StateMachine.PAD_WAIT_LEFT_PAD:
                //TORESOLVE
                resp = StateMachine.RESP_LEFT_PAD_FINISHED; // There is no mapping for this instruction
                NetworkProtocol.USER_RESPONSE = resp;
                instr = StateMachine.PAD_WAIT_LEFT_PAD;
                speechHelper.playInstructionSound(instr);
                break;

            case StateMachine.PAD_PEEL_RIGHT:
                //TORESOLVE
                NetworkProtocol.USER_RESPONSE = StateMachine.RESP_PEEL_PAD_RIGHT; // There is no mapping for this instruction
                resp = StateMachine.RESP_PEEL_PAD_RIGHT;
                instr = StateMachine.PAD_PEEL_RIGHT;
                speechHelper.playInstructionSound(instr);
                break;

            case StateMachine.PAD_WAIT_RIGHT_PAD:
                NetworkProtocol.USER_RESPONSE = StateMachine.RESP_RIGHT_PAD_FINISHED;
                resp = StateMachine.RESP_RIGHT_PAD_FINISHED;
                speechHelper.playInstructionSound(resp);
                break;

            case StateMachine.PAD_FINISH:
                NetworkProtocol.USER_RESPONSE = StateMachine.RESP_PAD_APPLYING_FINISHED;
                resp = StateMachine.RESP_PAD_APPLYING_FINISHED;
                instr = StateMachine.PAD_FINISH;
                speechHelper.playInstructionSound(instr);
                break;
        }
        Log.e(TAG,"resp = "+resp);
        Toast.makeText(getApplicationContext(),StateMachine.getRespStrByNum(resp),Toast.LENGTH_LONG).show();
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
            String modelResp;
            switch (field) {
                case AED_STATE:
                    currentState = model.aed_state;
                    Log.e(TAG,"currentState "+currentState);
                    mCache.put(TAG_CURRENT_STAGE, String.valueOf(currentState));
                    restoreSavedState();
                    mHiddenState.setText(String.valueOf(StateMachine.getStateStrByNum(currentState)));
                    speechHelper.playStateChangeSound(currentState);
                break;
                case TIMEOUT_STATE:
                    Log.e(TAG,"timeout_state "+currentState);
                    if(!is_voice_reco_running)
                        speechHelper.playTimeoutSound(currentState);
                break;
                case FRAME_AED:
                    isAEDFind = model.frame_aed;
                    break;
                case FRAME_ORANGE_BTN:
                    isOrangeButtonFind = model.frame_orange_btn;
                    break;
                case FRAME_YELLOW_PLUG:
                    isPlugFind = model.frame_yellow_plug;
                    break;
                case FRAME_ORANGE_FLASH:
                    isOrangeFlashFind = model.frame_orange_flash;
                    break;
                case PAD_Adult:
                    Log.e("suan","PAD_Adult");
                    mHiddenAdultPAD.setText(String.valueOf(model.pad_adult));
                    break;
                case PAD_WRONG_PAD:
                    modelResp = String.valueOf(model.pad_wrong_pad);
                    Log.e(TAG,"PAD_WRONG_PAD "+modelResp);
                    mHiddenWrongPad.setText(modelResp);
                    if (modelResp.equals("true")) {
                        speechHelper.playInstructionSound(field);
                    }
                    break;
                case PAD_DETECT:
                    if(model.aed_state == StateMachine.PAD_LEFT_PAD) {

                        Log.e("suan", "PAD_LEFT_PAD" + model.aed_state);
                        //First indicates up/down direction, second indicates left/right direction
                        //-1 means not detected, 1 means wrong
                        String tmp = model.pad_detect[0] + " " + model.pad_detect[1];
                        mHiddenPadDetect.setText(tmp);
                        // TODO: Update resp with actual values
                        int resp_1 = model.pad_detect[0];
                        int resp_2 = model.pad_detect[1];
                        if (resp_1 == 1) {
                            speechHelper.playLeftWrongPlacementSound();
                        }
                        if (resp_2 == 1) {
                            speechHelper.playLeftWrongViewSound();
                        }
                        // TODO: is this the case for correct placement?
                        if (resp_1 == 0 && resp_2 == 0) {
                            speechHelper.playInstructionSound(StateMachine.PAD_CORRECT_PAD);
                        }
                    }
                    if(model.aed_state == StateMachine.PAD_RIGHT_PAD) {
                        Log.e("suan", "PAD_RIGHT_PAD" + model.aed_state);
                        String tmp = model.pad_detect[0] + " " + model.pad_detect[1];
                        mHiddenPadDetect.setText(tmp);
                        // TODO: Update resp with actual values
                        int resp_1 = model.pad_detect[0];
                        int resp_2 = model.pad_detect[1];
                        if (resp_1 == 1) {
                            speechHelper.playRightWrongPlacementSound();
                        }
                        if (resp_2 == 1) {
                            speechHelper.playRightWrongViewSound();
                        }
                    }
                    break;
                case PAD_WRONG_LEFT:
                    if(model.aed_state == StateMachine.PAD_LEFT_PAD_SHOW) {
                        modelResp = String.valueOf(model.pad_wrong_left);
                        mHiddenWrongLeftPad.setText(modelResp);
                        if (modelResp.equals("true")) {
                            speechHelper.playInstructionSound(field);
                        }
                    }
                    break;
                case PATIENT_IS_ADULT:
                    if(model.aed_state == StateMachine.PAD_AGE_CONFIRM) {
                        modelResp = String.valueOf(model.patient_is_adult);
                        mHiddenPatientAdult.setText(modelResp);
                        if (modelResp.equals("0")) {
                            speechHelper.updateMapAdult(true);
                            speechHelper.playInstructionSound(field);
                            mCache.put(TAG_IS_ADULT, "0");
                        } else if(modelResp.equals("1")) {
                            speechHelper.updateMapAdult(false);
                            speechHelper.playInstructionSound(field);
                            mCache.put(TAG_IS_ADULT, "1");
                        }
                        Log.e(TAG,"PATIENT_IS_ADULT1 "+model.patient_is_adult);
//                        mHandler.postDelayed(mRunner, speechHelper.getInstrLen(field));
                    }else if(model.aed_state == StateMachine.PAD_NONE){
                        //means
                        modelResp = String.valueOf(model.patient_is_adult);
                        mHiddenPatientAdult.setText(modelResp);
                        if (modelResp.equals("0")) {
                            speechHelper.updateMapAdult(true);
                            mCache.put(TAG_IS_ADULT, "0");
                        } else if(modelResp.equals("1")) {
                            speechHelper.updateMapAdult(false);
                            mCache.put(TAG_IS_ADULT,"1");
                        }
                        Log.e(TAG,"PATIENT_IS_ADULT2 "+model.patient_is_adult);
                    }
                    break;
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
