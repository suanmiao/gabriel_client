package edu.cmu.cs.gabriel;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.HashMap;
import java.util.Locale;

/**
 * Created by suanmiao on 23/11/2016.
 */

public class SpeechHelper implements TextToSpeech.OnInitListener {
  private static final String LOG_TAG = "Speech";

  private TextToSpeech tts = null;
  private Context context;

  public SpeechHelper(Context context) {
    this.context = context;
    tts = new TextToSpeech(context, this);
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
  }
}
