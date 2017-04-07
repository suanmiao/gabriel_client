package edu.cmu.cs.gabriel;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import edu.cmu.cs.gabriel.StateMachine.StateUpdateEvent.Field;

import static edu.cmu.cs.gabriel.StateMachine.AED_FOUND;
import static edu.cmu.cs.gabriel.StateMachine.AED_NONE;
import static edu.cmu.cs.gabriel.StateMachine.AED_ON;
import static edu.cmu.cs.gabriel.StateMachine.AED_PLUGIN;
import static edu.cmu.cs.gabriel.StateMachine.AED_SHOCK;


import static edu.cmu.cs.gabriel.StateMachine.PAD_CORRECT_PAD;
import static edu.cmu.cs.gabriel.StateMachine.PAD_DEFIB_CONFIRM;
import static edu.cmu.cs.gabriel.StateMachine.PAD_FINISH;
import static edu.cmu.cs.gabriel.StateMachine.PAD_LEFT_PAD;
import static edu.cmu.cs.gabriel.StateMachine.PAD_LEFT_PAD_SHOW;
import static edu.cmu.cs.gabriel.StateMachine.PAD_PEEL_LEFT;
import static edu.cmu.cs.gabriel.StateMachine.PAD_PEEL_RIGHT;
import static edu.cmu.cs.gabriel.StateMachine.PAD_RIGHT_PAD;
import static edu.cmu.cs.gabriel.StateMachine.PAD_WAIT_LEFT_PAD;
import static edu.cmu.cs.gabriel.StateMachine.PAD_WAIT_RIGHT_PAD;
import static edu.cmu.cs.gabriel.StateMachine.RESP_DEFIB_NO;
import static edu.cmu.cs.gabriel.StateMachine.RESP_LEFT_PAD_FINISHED;
import static edu.cmu.cs.gabriel.StateMachine.RESP_PAD_APPLYING_FINISHED;
import static edu.cmu.cs.gabriel.StateMachine.RESP_PEEL_PAD_LEFT;
import static edu.cmu.cs.gabriel.StateMachine.RESP_PEEL_PAD_RIGHT;
import static edu.cmu.cs.gabriel.StateMachine.RESP_START_DETECTION;
import static edu.cmu.cs.gabriel.StateMachine.PAD_DETECT_AGE;
import static edu.cmu.cs.gabriel.StateMachine.StateUpdateEvent.Field.PAD_Adult;
import static edu.cmu.cs.gabriel.StateMachine.StateUpdateEvent.Field.PAD_CORR_DETECT;
import static edu.cmu.cs.gabriel.StateMachine.StateUpdateEvent.Field.PAD_CORR_DETECT_RIGHT;
import static edu.cmu.cs.gabriel.StateMachine.StateUpdateEvent.Field.PAD_CORR_LEFT;
import static edu.cmu.cs.gabriel.StateMachine.StateUpdateEvent.Field.PAD_CORR_PAD;
import static edu.cmu.cs.gabriel.StateMachine.StateUpdateEvent.Field.PAD_DETECT;
import static edu.cmu.cs.gabriel.StateMachine.StateUpdateEvent.Field.PAD_DETECT_RIGHT;
import static edu.cmu.cs.gabriel.StateMachine.StateUpdateEvent.Field.PAD_WRONG_LEFT;
import static edu.cmu.cs.gabriel.StateMachine.StateUpdateEvent.Field.PATIENT_IS_ADULT;
import static edu.cmu.cs.gabriel.StateMachine.RESP_AGE_DETECT_YES;
import static edu.cmu.cs.gabriel.StateMachine.RESP_AGE_DETECT_NO;
import static edu.cmu.cs.gabriel.StateMachine.StateUpdateEvent.Field.PAD_WRONG_PAD;
import static edu.cmu.cs.gabriel.StateMachine.RESP_DEFIB_YES;
import static edu.cmu.cs.gabriel.StateMachine.TIMEOUT_NONE;

/**
 * Created by suanmiao on 23/11/2016.
 */

public class SpeechHelper implements TextToSpeech.OnInitListener {
  private static final String LOG_TAG = "Speech";

  private MediaPlayer player;

  private TextToSpeech tts = null;
  private Context context;
  public HashMap<Integer, String> aedStageInstructionMap = new HashMap<Integer, String>();
  public HashMap<Integer, String> padStageInstructionMap = new HashMap<Integer, String>();
  public HashMap<Integer, String> timeoutInstructionMap = new HashMap<Integer, String>();
  public HashMap<Integer, String> respInstructionMap = new HashMap<Integer, String>();
  public HashMap<Field, String> fieldInstructionMap = new HashMap<Field, String>();
  public HashMap<Integer, Integer> padStageInstructionLen = new HashMap<Integer, Integer>();
  public HashMap<Integer, Integer> respInstructionLen = new HashMap<Integer, Integer>();
  public HashMap<Field, Integer> fieldInstructionLen = new HashMap<Field, Integer>();

//  private List<String> initialFiles = Arrays.asList("instr_1.m4a","instr_2.m4a",
//          "instr_3.m4a","instr_4.m4a","instr_5a.m4a");
  private List<String> initialFiles = Arrays.asList("instr_5a.m4a");
  private int initialCounter = 0;
  private int initialStages = initialFiles.size();

  private MediaPlayer initialPlayer;

  public boolean is_finished = true;

  List<String> waitingAudios = new ArrayList<>();

  public SpeechHelper(Context context) {
    this.context = context;
    //tts = new TextToSpeech(context, this);
    this.player = new MediaPlayer();

    //stage instruction for AED stage.
    aedStageInstructionMap.put(AED_NONE, "01_look_at.wav");
    aedStageInstructionMap.put(AED_FOUND, "02_turn_on.wav");
    aedStageInstructionMap.put(AED_ON, "03_apply_pad.wav");
    aedStageInstructionMap.put(AED_PLUGIN, "04_wait_further.wav");
    aedStageInstructionMap.put(AED_SHOCK, "05_press_shock.wav");

    //audio for user's action
    padStageInstructionMap.put(RESP_START_DETECTION, "instr_5b.m4a");
    padStageInstructionMap.put(PAD_DEFIB_CONFIRM,"instr_7.m4a");
    padStageInstructionMap.put(PAD_LEFT_PAD_SHOW,"instr_8.m4a");
    padStageInstructionMap.put(PAD_PEEL_LEFT,"instr_9.m4a");
    padStageInstructionMap.put(PAD_LEFT_PAD, "instr_11.m4a");
    padStageInstructionMap.put(PAD_PEEL_RIGHT,"instr_12.m4a");
    padStageInstructionMap.put(PAD_WAIT_RIGHT_PAD,"instr_13.m4a");
    padStageInstructionMap.put(PAD_RIGHT_PAD,"instr_14.m4a");
    padStageInstructionMap.put(PAD_FINISH,"instr_15.m4a");

    //Pre-AED instructions common to all groups (adult/child/bulge/nobulge)
    respInstructionMap.put(RESP_PAD_APPLYING_FINISHED, "instr_16.m4a");

    //instruction for timeout
    timeoutInstructionMap.put(AED_NONE, "06_no_aed.wav");
    timeoutInstructionMap.put(AED_FOUND, "7.wav");
    timeoutInstructionMap.put(AED_ON, "07_no_plug.wav");
    timeoutInstructionMap.put(AED_PLUGIN, "08_no_shock.wav");
    timeoutInstructionMap.put(AED_SHOCK, "10.wav");

    padStageInstructionLen.put(PAD_DEFIB_CONFIRM, 9500);
    padStageInstructionLen.put(PAD_PEEL_LEFT, 6500);
    padStageInstructionLen.put(PAD_PEEL_RIGHT, 9500);
    padStageInstructionLen.put(PAD_FINISH, 10500);

    Log.e("###", "Setting up");

    try {
      this.initialPlayer = new MediaPlayer();

      AssetFileDescriptor descriptor = context.getAssets().openFd("instr_1.m4a");
      initialPlayer.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(),
              descriptor.getLength());
      descriptor.close();

      initialPlayer.prepare();
      initialPlayer.setVolume(1f, 1f);
      initialPlayer.setLooping(false);
      initialPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
        public void onCompletion(MediaPlayer mp) {
          playInitialStages();
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
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
      fieldInstructionMap.put(PATIENT_IS_ADULT, "instr_5c_adult.m4a");
      fieldInstructionLen.put(PATIENT_IS_ADULT, 6500);
      padStageInstructionMap.put(PAD_CORRECT_PAD,"instr_6_blue.m4a");
      fieldInstructionMap.put(PAD_WRONG_PAD, "instr_6_blue_err.m4a");
    } else {
      fieldInstructionMap.put(PATIENT_IS_ADULT, "instr_5c_child.m4a");
      fieldInstructionLen.put(PATIENT_IS_ADULT, 6500);
      padStageInstructionMap.put(PAD_CORRECT_PAD,"instr_6_red.m4a");
      fieldInstructionMap.put(PAD_WRONG_PAD, "instr_6_red_err.m4a");
    }
  }

  // Add instructions according to whether the patient has a defibrillator
  public void updateMapDefib(boolean hasDefib) {
    if (hasDefib) {
      respInstructionMap.put(RESP_DEFIB_YES, "instr_8.m4a");
      padStageInstructionMap.put(PAD_WAIT_LEFT_PAD, "instr_10_bulge.m4a");
      padStageInstructionLen.put(PAD_WAIT_LEFT_PAD, 15200);
      fieldInstructionMap.put(PAD_DETECT, "instr_11_err_bulge.m4a");
    } else {
      respInstructionMap.put(RESP_DEFIB_NO, "instr_8.m4a");
      padStageInstructionMap.put(PAD_WAIT_LEFT_PAD, "instr_10_nobulge.m4a");
      padStageInstructionLen.put(PAD_WAIT_LEFT_PAD, 15200);
      fieldInstructionMap.put(PAD_DETECT, "instr_11_err_nobulge.m4a");
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
    playSound(assetPath);
  }

  public void playStateChangeSound(int stage){
    String assetPath;
    if (padStageInstructionMap.containsKey(stage)) {
      assetPath = padStageInstructionMap.get(stage);
      Log.e("state audio path",assetPath);
    }else if(aedStageInstructionMap.containsKey(stage)){
      assetPath = aedStageInstructionMap.get(stage);
      Log.e("pad path",assetPath);
    }else {
      return;
    }
    playSound(assetPath);
  }

  public void playAEDStageInstruction(int stage){
    String assetPath;
    assetPath = aedStageInstructionMap.get(stage);
    playSound(assetPath);
  }

  public void playTimeoutSound(int stage) {
    if (!timeoutInstructionMap.containsKey(stage)) {
      return;
    }
    String assetPath = timeoutInstructionMap.get(stage);
    playSound(assetPath);
  }

  public void playInitialStages() {
    if (initialCounter == 0) {
      initialPlayer.start();
      initialCounter++;
    } else if (initialCounter < initialStages) {
      String assetPath = initialFiles.get(initialCounter);
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
            playInitialStages();
          }
        });
        initialPlayer.start();
        Log.e("Playing initial instr:", "playing " + assetPath);
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      Log.e("Stopped playing init at", "#"+initialCounter+"vs"+initialStages);
      initialPlayer.release();
    }
  }

  private void playSound(String path) {
    if (!is_finished){
      return;
    }
    is_finished = false;
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
}
