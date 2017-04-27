package edu.cmu.cs.gabriel;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import edu.cmu.cs.gabriel.StateMachine.StateUpdateEvent.Field;

import static edu.cmu.cs.gabriel.StateMachine.AED_FOUND;
import static edu.cmu.cs.gabriel.StateMachine.AED_NONE;
import static edu.cmu.cs.gabriel.StateMachine.AED_ON;
import static edu.cmu.cs.gabriel.StateMachine.AED_PLUGIN;
import static edu.cmu.cs.gabriel.StateMachine.AED_SHOCK;


import static edu.cmu.cs.gabriel.StateMachine.PAD_COMFIRM_PAD;
import static edu.cmu.cs.gabriel.StateMachine.PAD_CORRECT_PAD;
import static edu.cmu.cs.gabriel.StateMachine.PAD_DEFIB_CONFIRM;
import static edu.cmu.cs.gabriel.StateMachine.PAD_DETECT_AGE;
import static edu.cmu.cs.gabriel.StateMachine.PAD_FINISH;
import static edu.cmu.cs.gabriel.StateMachine.PAD_LEFT_PAD;
import static edu.cmu.cs.gabriel.StateMachine.PAD_LEFT_PAD_SHOW;
import static edu.cmu.cs.gabriel.StateMachine.PAD_NONE;
import static edu.cmu.cs.gabriel.StateMachine.PAD_PEEL_LEFT;
import static edu.cmu.cs.gabriel.StateMachine.PAD_PEEL_RIGHT;
import static edu.cmu.cs.gabriel.StateMachine.PAD_PRE_1;
import static edu.cmu.cs.gabriel.StateMachine.PAD_PRE_2;
import static edu.cmu.cs.gabriel.StateMachine.PAD_RIGHT_PAD;
import static edu.cmu.cs.gabriel.StateMachine.PAD_WAIT_LEFT_PAD;
import static edu.cmu.cs.gabriel.StateMachine.PAD_WAIT_RIGHT_PAD;
import static edu.cmu.cs.gabriel.StateMachine.RESP_DEFIB_NO;
import static edu.cmu.cs.gabriel.StateMachine.RESP_PAD_APPLYING_FINISHED;
import static edu.cmu.cs.gabriel.StateMachine.StateUpdateEvent.Field.PAD_DETECT;
import static edu.cmu.cs.gabriel.StateMachine.StateUpdateEvent.Field.PATIENT_IS_ADULT;
import static edu.cmu.cs.gabriel.StateMachine.StateUpdateEvent.Field.PAD_WRONG_PAD;
import static edu.cmu.cs.gabriel.StateMachine.RESP_DEFIB_YES;
import static edu.cmu.cs.gabriel.StateMachine.TAG;

/**
 * Created by suanmiao on 23/11/2016.
 */

public class SpeechHelper implements TextToSpeech.OnInitListener {

  private static final String LOG_TAG = "Speech";

  private MediaPlayer player;

  private TextToSpeech tts = null;
  private Context context;
  private Handler mVoiceInput = null;
  public HashMap<Integer, String> aedStageInstructionMap = new HashMap<Integer, String>();
  public HashMap<Integer, String> padStageInstructionMap = new HashMap<Integer, String>();
  public HashMap<Integer, String> timeoutInstructionMap = new HashMap<Integer, String>();
  public HashMap<Integer, String> respInstructionMap = new HashMap<Integer, String>();
  public HashMap<Field, String> fieldInstructionMap = new HashMap<Field, String>();
  public HashMap<Integer, Integer> padStageInstructionLen = new HashMap<Integer, Integer>();
  public HashMap<Integer, Integer> respInstructionLen = new HashMap<Integer, Integer>();
  public HashMap<Field, Integer> fieldInstructionLen = new HashMap<Field, Integer>();

  private MediaPlayer initialPlayer = new MediaPlayer();
  int initialCounter = 0;
  final List<String> pre2Files = Arrays.asList("New_2.m4a",
          "New_3.m4a", "New_4.m4a");
  int initialStages = pre2Files.size();

  public boolean is_finished = true;

  List<String> waitingAudios = new ArrayList<>();

  public SpeechHelper(Context context) {
    this.context = context;
    this.player = new MediaPlayer();

    //audio for user's action
    padStageInstructionMap.put(PAD_PRE_1,"New_1.m4a");
    padStageInstructionMap.put(PAD_DETECT_AGE, "instr_5b.m4a");

    padStageInstructionMap.put(PAD_NONE,"New_6.m4a");

//    padStageInstructionMap.put(PAD_COMFIRM_PAD,"New_9.m4a");
    padStageInstructionMap.put(PAD_COMFIRM_PAD,"Modify_9.m4a");
//    padStageInstructionMap.put(PAD_DEFIB_CONFIRM,"New_10.m4a");
    padStageInstructionMap.put(PAD_DEFIB_CONFIRM,"Modify_10.m4a");
    padStageInstructionMap.put(PAD_LEFT_PAD_SHOW,"New_11.m4a");
    padStageInstructionMap.put(PAD_PEEL_LEFT,"New_12.m4a");
//    padStageInstructionMap.put(PAD_LEFT_PAD, "New_14.m4a");
    padStageInstructionMap.put(PAD_LEFT_PAD, "Modify_14.m4a");

    padStageInstructionMap.put(PAD_PEEL_RIGHT,"New_15.m4a");
    padStageInstructionMap.put(PAD_WAIT_RIGHT_PAD,"New_16.m4a");

//    padStageInstructionMap.put(PAD_RIGHT_PAD,"New_17.m4a");
    padStageInstructionMap.put(PAD_RIGHT_PAD,"Modify_17.m4a");

    padStageInstructionMap.put(PAD_FINISH,"New_18.m4a");
    padStageInstructionMap.put(AED_NONE,"New_19.m4a");

    aedStageInstructionMap.put(AED_PLUGIN, "04_wait_further.wav");

    respInstructionMap.put(RESP_PAD_APPLYING_FINISHED, "New_19.m4a");

    //instruction for timeout
    timeoutInstructionMap.put(PAD_PRE_1,"instr_1.m4a");
    timeoutInstructionMap.put(PAD_PRE_2,"New_4_timeout.m4a");

    timeoutInstructionMap.put(PAD_NONE,"New_6.m4a");
    //correct pad added
    timeoutInstructionMap.put(PAD_COMFIRM_PAD,"Modify_9.m4a");
//    timeoutInstructionMap.put(PAD_COMFIRM_PAD,"New_9.m4a");
//    timeoutInstructionMap.put(PAD_DEFIB_CONFIRM,"New_10.m4a");
    timeoutInstructionMap.put(PAD_DEFIB_CONFIRM,"Modify_10.m4a");
    timeoutInstructionMap.put(PAD_LEFT_PAD_SHOW,"New_11.m4a");
//    timeoutInstructionMap.put(PAD_PEEL_LEFT,"New_12.m4a");
    timeoutInstructionMap.put(PAD_PEEL_LEFT,"Modify_12.m4a");
    timeoutInstructionMap.put(PAD_LEFT_PAD, "Modify_14.m4a");

//    timeoutInstructionMap.put(PAD_LEFT_PAD, "New_14.m4a");

    timeoutInstructionMap.put(PAD_PEEL_RIGHT,"New_15.m4a");
    timeoutInstructionMap.put(PAD_WAIT_RIGHT_PAD,"New_16.m4a");

    timeoutInstructionMap.put(PAD_RIGHT_PAD,"New_17.m4a");
    timeoutInstructionMap.put(PAD_FINISH,"New_18.m4a");
    timeoutInstructionMap.put(AED_NONE,"New_19.m4a");

    timeoutInstructionMap.put(AED_FOUND, "06_no_aed.wav");
//    timeoutInstructionMap.put(AED_FOUND, "7.wav");
//    timeoutInstructionMap.put(AED_ON, "07_no_plug.wav");
//    timeoutInstructionMap.put(AED_PLUGIN, "08_no_shock.wav");
//    timeoutInstructionMap.put(AED_SHOCK, "10.wav");
//    timeoutInstructionMap.put(AED_PLUGIN,"04_wait_further.wav");

    padStageInstructionLen.put(PAD_DEFIB_CONFIRM, 9500);
    padStageInstructionLen.put(PAD_PEEL_LEFT, 6500);
    padStageInstructionLen.put(PAD_PEEL_RIGHT, 9500);
    padStageInstructionLen.put(PAD_FINISH, 10500);
  }

  Random rnd = null;
  private boolean playOKInstructionRandomly(final String assetPath,final int stage){

    if(rnd == null){
      rnd = new Random();
      rnd.setSeed(System.currentTimeMillis());
    }
    int num = rnd.nextInt();
    Log.e(LOG_TAG,"test num: "+String.valueOf(num));
    if(num % 2 == 0){
      return false;
    }
    try {
      player = new MediaPlayer();
      AssetFileDescriptor descriptor = context.getAssets().openFd("Ok.m4a");
      Log.e("GabrielClient", "length == " + descriptor.getLength());
      player.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(),
              descriptor.getLength());
      descriptor.close();
      player.prepare();
      player.setVolume(1f, 1f);
      player.setLooping(false);
      player.start();
      player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
          is_finished = true;
          playSound(assetPath, stage);
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
    }
    return true;
  }

  /*
   * Play the instructions for PAD_PRE_2
   */
  private void playPre2Instructions(){

    Log.e(TAG,"playPre2Instructions");
    try {
        AssetFileDescriptor descriptor = context.getAssets().openFd("instr_2.m4a");
        initialPlayer.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(),
              descriptor.getLength());
        descriptor.close();

        initialPlayer.prepare();
        initialPlayer.setVolume(1f, 1f);
        initialPlayer.setLooping(false);
        initialPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
          public void onCompletion(MediaPlayer mp) {
            playPre2InstructionsAudio();
          }
        });
      initialPlayer.start();
      initialCounter++;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
  * Implementation for play the instructions for PAD_PRE_2
  */
  private void playPre2InstructionsAudio() {
    if (initialCounter == 0) {
        initialPlayer.start();
        initialCounter++;
    } else if (initialCounter < initialStages) {
      String assetPath = pre2Files.get(initialCounter);
      initialCounter++;

      try {
        if (initialPlayer != null) {
          if (initialPlayer.isPlaying()) {
            initialPlayer.stop();
          }
          initialPlayer.reset();
          initialPlayer = new MediaPlayer();
        }

        AssetFileDescriptor descriptor = context.getAssets().openFd(assetPath);
        initialPlayer.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(),
                descriptor.getLength());
        descriptor.close();

        initialPlayer.prepare();
        initialPlayer.setVolume(1f, 1f);
        initialPlayer.setLooping(false);
        initialPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
          public void onCompletion(MediaPlayer mp) {
            playPre2InstructionsAudio();
          }
        });
        initialPlayer.start();
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      initialPlayer.release();
      mVoiceInput.sendEmptyMessageDelayed(PAD_PRE_2, 1000);
    }
  }


  public int getInstrLen(int stage) {
    if (padStageInstructionLen.containsKey(stage)) {
      return padStageInstructionLen.get(stage);
    } else if (respInstructionLen.containsKey(stage)) {
      return respInstructionLen.get(stage);
    } else {
      return 0;
    }
  }

  public int getInstrLen(Field field) {
    if (fieldInstructionLen.containsKey(field)) {
      return fieldInstructionLen.get(field);
    } else {
      return 0;
    }
  }

  // Add instructions according to whether the patient is an adult
  public void updateMapAdult(boolean isAdult) {
    // Remove from fields first because there may have been an age detection error
    fieldInstructionMap.remove(PATIENT_IS_ADULT);
    padStageInstructionMap.remove(PAD_CORRECT_PAD);
    fieldInstructionMap.remove(PAD_WRONG_PAD);
    if (isAdult) {
      fieldInstructionMap.put(PATIENT_IS_ADULT, "New_5_adult.m4a");
      padStageInstructionMap.put(PAD_CORRECT_PAD,"New_8_adult.m4a");
      timeoutInstructionMap.put(PAD_CORRECT_PAD,"New_8_adult.m4a");
      fieldInstructionMap.put(PAD_WRONG_PAD, "New_8_adult_err.m4a");
    } else {
      fieldInstructionMap.put(PATIENT_IS_ADULT, "New_5_child.m4a");
      padStageInstructionMap.put(PAD_CORRECT_PAD,"New_8_child.m4a");
      timeoutInstructionMap.put(PAD_CORRECT_PAD,"New_8_child.m4a");
      fieldInstructionMap.put(PAD_WRONG_PAD, "New_8_child_err.m4a");
    }
  }

  // Add instructions according to whether the patient has a defibrillator
  public void updateMapDefib(boolean hasDefib) {
    if (hasDefib) {
      respInstructionMap.put(RESP_DEFIB_YES, "New_11.m4a");
      padStageInstructionMap.put(PAD_WAIT_LEFT_PAD, "New_13_bulge.m4a");
      fieldInstructionMap.put(PAD_DETECT, "New_14_err_bulge.m4a");
    } else {
      respInstructionMap.put(RESP_DEFIB_NO, "New_11.m4a");
      padStageInstructionMap.put(PAD_WAIT_LEFT_PAD, "New_13_nobulge.m4a");
      fieldInstructionMap.put(PAD_DETECT, "New_14_err_nobulge.m4a");
    }
  }

  public void playLeftWrongViewSound() {
    String assetPath = "instr_11_err_view.m4a";
    playSound(assetPath);
  }

  public void playRightWrongViewSound() {
    String assetPath = "instr_14_err_view.m4a";
    playSound(assetPath);
  }

  public void playLeftWrongPlacementSound(){
    String assetPath = fieldInstructionMap.get(PAD_DETECT);
    playSound(assetPath);
  }

  public void playRightWrongPlacementSound(){
    String assetPath = "instr_14_err_placement.m4a";
    playSound(assetPath);
  }

  public void playInstructionSound(Field f) {
    if (!fieldInstructionMap.containsKey(f)) {
      return;
    }
    String assetPath = fieldInstructionMap.get(f);
    if(f == PATIENT_IS_ADULT)
      playSound(assetPath,StateMachine.PAD_AGE_CONFIRM);
    else
      playSound(assetPath);
  }

  public void playInstructionSound(int stage) {

    String assetPath;
    if (padStageInstructionMap.containsKey(stage)) {
      assetPath = padStageInstructionMap.get(stage);
      Log.e("instru audio path",assetPath);
    } else if (respInstructionMap.containsKey(stage)) {
      assetPath = respInstructionMap.get(stage);
    }else{
      return;
    }
    if(!playOKInstructionRandomly(assetPath,stage))
      playSound(assetPath,stage);
  }

  public void playStateChangeSound(int stage){

    if(stage == PAD_PRE_2){
      playPre2Instructions();
      return;
    }
    String assetPath;
    if (padStageInstructionMap.containsKey(stage)) {
      assetPath = padStageInstructionMap.get(stage);
      Log.e("GabrielClient","pad audio path "+assetPath);
    }else if(aedStageInstructionMap.containsKey(stage)){
      assetPath = aedStageInstructionMap.get(stage);
      Log.e("GabrielClient","aed audio path "+assetPath);
    }else {
      return;
    }
    if(stage == PAD_PRE_1){
      playSound(assetPath, stage);
    }else {
      if (!playOKInstructionRandomly(assetPath, stage)) {
        playSound(assetPath, stage);
      }
    }
  }

  public void playAEDStageInstruction(int stage){
    String assetPath;
    assetPath = aedStageInstructionMap.get(stage);
    playSound(assetPath,stage);
  }

  public void playTimeoutSound(int stage) {
    if (!timeoutInstructionMap.containsKey(stage)) {
      return;
    }
    String assetPath = timeoutInstructionMap.get(stage);
    Log.e(TAG,"timeout audio "+assetPath);
    playSound(assetPath,stage);
  }

  private void playSound(String path) {
//    if (!is_finished) {
//      return;
//    }
//    is_finished = false;
    Log.e("suan play sound", "path " + path);
    try {
      if (player != null) {
        if (player.isPlaying()) {
          player.stop();
          is_finished = true;
        }
        player.reset();
        player = new MediaPlayer();
      }

      AssetFileDescriptor descriptor = context.getAssets().openFd(path);
      player.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(),
              descriptor.getLength());
      descriptor.close();


      player.prepare();
      player.setVolume(1f, 1f);
      player.setLooping(false);
      player.start();
      player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

        @Override
        public void onCompletion(MediaPlayer mp) {
          is_finished = true;
        }
      });
      Log.e("suan play sound", "play start " + path);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void playSound(String path, final int stage) {
    Log.e("GabrielClient", "playSound");
//    if (!is_finished) {
//      return;
//    }
    is_finished = false;
    Log.e("GabrielClient", "path " + path);
    try {
        if (player != null) {
//          if (player.isPlaying()) {
//            Log.e("GabrielClient", "2 ");
//            player.stop();
//            is_finished = true;
//          }
//          player.reset();
        }
      player = new MediaPlayer();

      AssetFileDescriptor descriptor = context.getAssets().openFd(path);
      Log.e("GabrielClient","length == "+descriptor.getLength());
      player.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(),
              descriptor.getLength());
      descriptor.close();


      player.prepare();
      player.setVolume(1f, 1f);
      player.setLooping(false);
      player.start();
      Log.e("GabrielClient", "player start ");
      player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
           is_finished = true;
           Log.e("GabrielClient", "play finished ");
           mVoiceInput.sendEmptyMessage(stage);
        }
      });

      Log.e("GabrielClient", "play start " + path);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void speech(String ttsMessage) {
    if (tts != null && !tts.isSpeaking()) {
      //tts.setSpeechRate(1.5f);
      tts.setSpeechRate(1f);
      String[] splitMSGs = ttsMessage.split("\\.");
      HashMap<String, String> map = new HashMap<String, String>();
      map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "unique");

      if (splitMSGs.length == 1) {
        tts.speak(splitMSGs[0].toString().trim(), TextToSpeech.QUEUE_FLUSH,
            map); // the only sentence
      } else {
        tts.speak(splitMSGs[0].toString().trim(), TextToSpeech.QUEUE_FLUSH,
            null); // the first sentence
        for (int i = 1; i < splitMSGs.length - 1; i++) {
          tts.playSilence(350, TextToSpeech.QUEUE_ADD, null); // add pause for every period
          tts.speak(splitMSGs[i].toString().trim(), TextToSpeech.QUEUE_ADD, null);
        }
        tts.playSilence(350, TextToSpeech.QUEUE_ADD, null);
        tts.speak(splitMSGs[splitMSGs.length - 1].toString().trim(), TextToSpeech.QUEUE_ADD,
            map); // the last sentence
      }
    }
  }

  /**************** TextToSpeech.OnInitListener ***************/
  public void onInit(int status) {
    if (status == TextToSpeech.SUCCESS) {
      if (tts == null) {
        tts = new TextToSpeech(context, this);
      }
      int result = tts.setLanguage(Locale.US);
      if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
        Log.e(LOG_TAG, "Language is not available.");
      }
      int listenerResult = tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
        @Override public void onDone(String utteranceId) {
          Log.v(LOG_TAG, "progress on Done " + utteranceId);
          //                  notifyToken();
        }

        @Override public void onError(String utteranceId) {
          Log.v(LOG_TAG, "progress on Error " + utteranceId);
        }

        @Override public void onStart(String utteranceId) {
          Log.v(LOG_TAG, "progress on Start " + utteranceId);
        }
      });
      if (listenerResult != TextToSpeech.SUCCESS) {
        Log.e(LOG_TAG, "failed to add utterance progress listener");
      }
    } else {
      // Initialization failed.
      Log.e(LOG_TAG, "Could not initialize TextToSpeech.");
    }
  }

  /**************** End of TextToSpeech.OnInitListener ********/

  public void stop() {
    if (tts != null) {
      tts.stop();
      tts.shutdown();
      tts = null;
    }
    if (player != null) {
      player.release();
    }
  }

  public Handler getVoiceInput() {
    return mVoiceInput;
  }

  public void setVoiceInput(Handler mVoiceInput) {
    this.mVoiceInput = mVoiceInput;
  }
}
