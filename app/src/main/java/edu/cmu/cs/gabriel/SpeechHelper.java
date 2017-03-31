package edu.cmu.cs.gabriel;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

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

import static edu.cmu.cs.gabriel.StateMachine.RESP_DEFIB_NO;
import static edu.cmu.cs.gabriel.StateMachine.RESP_LEFT_PAD_FINISHED;
import static edu.cmu.cs.gabriel.StateMachine.RESP_PAD_APPLYING_FINISHED;
import static edu.cmu.cs.gabriel.StateMachine.RESP_PEEL_PAD_LEFT;
import static edu.cmu.cs.gabriel.StateMachine.RESP_PEEL_PAD_RIGHT;
import static edu.cmu.cs.gabriel.StateMachine.RESP_START_DETECTION;
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
import static edu.cmu.cs.gabriel.StateMachine.StateUpdateEvent.Field.PAD_WRONG_PAD;
import static edu.cmu.cs.gabriel.StateMachine.RESP_DEFIB_YES;

/**
 * Created by suanmiao on 23/11/2016.
 */

public class SpeechHelper implements TextToSpeech.OnInitListener {
  private static final String LOG_TAG = "Speech";

  private MediaPlayer player;

  private TextToSpeech tts = null;
  private Context context;
  public HashMap<Integer, String> stageInstructionMap = new HashMap<Integer, String>();
  public HashMap<Integer, String> timeoutInstructionMap = new HashMap<Integer, String>();
  public HashMap<Integer, String> respInstructionMap = new HashMap<Integer, String>();
  public HashMap<Field, String> fieldInstructionMap = new HashMap<Field, String>();

  private List<String> initialFiles = Arrays.asList("instr_1.m4a","instr_2.m4a",
          "instr_3.m4a","instr_4.m4a","instr_5a.m4a");
  private int initialCounter = 0;
  private int initialStages = initialFiles.size();

  private MediaPlayer initialPlayer;

  public SpeechHelper(Context context) {
    this.context = context;
    //tts = new TextToSpeech(context, this);
    this.player = new MediaPlayer();

    stageInstructionMap.put(AED_NONE, "01_look_at.wav");
    stageInstructionMap.put(AED_FOUND, "02_turn_on.wav");
    stageInstructionMap.put(AED_ON, "03_apply_pad.wav");
    stageInstructionMap.put(AED_PLUGIN, "04_wait_further.wav");
    stageInstructionMap.put(AED_SHOCK, "05_press_shock.wav");

    //Pre-AED instructions common to all groups (adult/child/bulge/nobulge)
    stageInstructionMap.put(RESP_START_DETECTION, "instr_5b.m4a");
    stageInstructionMap.put(RESP_PEEL_PAD_RIGHT, "instr_13.m4a");

    respInstructionMap.put(RESP_LEFT_PAD_FINISHED, "instr_11.m4a");
    respInstructionMap.put(RESP_PAD_APPLYING_FINISHED, "instr_16.m4a");

    fieldInstructionMap.put(PAD_CORR_PAD, "instr_7.m4a");
    fieldInstructionMap.put(PAD_WRONG_LEFT, "instr_8_err.m4a");
    fieldInstructionMap.put(PAD_CORR_LEFT, "instr_9.m4a");
    fieldInstructionMap.put(PAD_CORR_DETECT, "instr_12.m4a");
    fieldInstructionMap.put(PAD_DETECT_RIGHT, "instr_14_err_placement.m4a");
    fieldInstructionMap.put(PAD_CORR_DETECT_RIGHT, "instr_15.m4a");

    timeoutInstructionMap.put(AED_NONE, "06_no_aed.wav");
    timeoutInstructionMap.put(AED_FOUND, "7.wav");
    timeoutInstructionMap.put(AED_ON, "07_no_plug.wav");
    timeoutInstructionMap.put(AED_PLUGIN, "08_no_shock.wav");
    timeoutInstructionMap.put(AED_SHOCK, "10.wav");

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

  // Add instructions according to whether the patient is an adult
  public void updateMapAdult(boolean isAdult) {
    // Remove from fields first because there may have been an age detection error
    fieldInstructionMap.remove(PATIENT_IS_ADULT);
    respInstructionMap.remove(RESP_AGE_DETECT_YES);
    fieldInstructionMap.remove(PAD_WRONG_PAD);
    if (isAdult) {
      fieldInstructionMap.put(PATIENT_IS_ADULT, "instr_5c_adult.m4a");
      respInstructionMap.put(RESP_AGE_DETECT_YES, "instr_6_blue.m4a");
      fieldInstructionMap.put(PAD_WRONG_PAD, "instr_6_blue_err.m4a");
    } else {
      fieldInstructionMap.put(PATIENT_IS_ADULT, "instr_5c_child.m4a");
      respInstructionMap.put(RESP_AGE_DETECT_YES, "instr_6_red.m4a");
      fieldInstructionMap.put(PAD_WRONG_PAD, "instr_6_red_err.m4a");
    }
  }

  // Add instructions according to whether the patient has a defibrillator
  public void updateMapDefib(boolean hasDefib) {
    if (hasDefib) {
      respInstructionMap.put(RESP_DEFIB_YES, "instr_8.m4a");
      respInstructionMap.put(RESP_PEEL_PAD_LEFT, "instr_10_bulge.m4a");
      fieldInstructionMap.put(PAD_DETECT, "instr_11_err_bulge.m4a");
    } else {
      respInstructionMap.put(RESP_DEFIB_NO, "instr_8.m4a");
      respInstructionMap.put(RESP_PEEL_PAD_LEFT, "instr_10_nobulge.m4a");
      fieldInstructionMap.put(PAD_DETECT, "instr_11_err_nobulge.m4a");
    }
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
    if (stageInstructionMap.containsKey(stage)) {
      assetPath = stageInstructionMap.get(stage);
    } else if (respInstructionMap.containsKey(stage)) {
      assetPath = respInstructionMap.get(stage);
    } else if (timeoutInstructionMap.containsKey(stage)){
      assetPath = timeoutInstructionMap.get(stage);
    } else {
      return;
    }
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
      initialPlayer.release();
    }
  }

  private void playSound(String path) {
    Log.e("suan play sound", "path " + path);
    try {
      if (player != null) {
        if (player.isPlaying()) {
          player.stop();
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
