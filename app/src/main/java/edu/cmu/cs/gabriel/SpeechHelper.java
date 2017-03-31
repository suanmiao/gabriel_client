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

import static edu.cmu.cs.gabriel.StateMachine.AED_FOUND;
import static edu.cmu.cs.gabriel.StateMachine.AED_NONE;
import static edu.cmu.cs.gabriel.StateMachine.AED_ON;
import static edu.cmu.cs.gabriel.StateMachine.AED_PLUGIN;
import static edu.cmu.cs.gabriel.StateMachine.AED_SHOCK;
import static edu.cmu.cs.gabriel.StateMachine.PREP_A;
import static edu.cmu.cs.gabriel.StateMachine.PREP_B;
import static edu.cmu.cs.gabriel.StateMachine.PREP_C;
import static edu.cmu.cs.gabriel.StateMachine.PREP_D;
import static edu.cmu.cs.gabriel.StateMachine.PREP_E;
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

  private List<String> initialFiles = Arrays.asList("instr_1.m4a","instr_2.m4a",
          "instr_3.m4a","instr_4.m4a","instr_5.m4a");
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

    //Preprocessing patient stage
    stageInstructionMap.put(PREP_A, "instr_1.m4a");
    stageInstructionMap.put(PREP_B, "instr_2.m4a");
    stageInstructionMap.put(PREP_C, "instr_3.m4a");
    stageInstructionMap.put(PREP_D, "instr_4.m4a");
    stageInstructionMap.put(PREP_E, "instr_5a.m4a");

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

  public void playInstructionSound(int stage) {
    if (!stageInstructionMap.containsKey(stage)) {
      return;
    }
    String assetPath = stageInstructionMap.get(stage);
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
