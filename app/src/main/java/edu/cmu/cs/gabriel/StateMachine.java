package edu.cmu.cs.gabriel;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by suanmiao on 23/11/2016.
 */

public class StateMachine {
  public static StateMachine instance;
  public static final int STATE_NONE = -1;
  public static final int STATE_AED_FOUND = 0;
  public static final int STATE_AED_ON = 1;
  public static final int STATE_AED_PLUGIN = 2;
  public static final int STATE_AED_SHOCK = 3;

  private int state = STATE_NONE;
  private Handler uiHandler;
  public HashMap<Integer, String> stageVoiceMap = new HashMap<Integer, String>();

  public static class StateModel {
    public int aed_state;
    public String tpod_state;
  }

  public interface StateChangeCallback {
    public void onChange(int prevState, int currentState);
  }

  public List<StateChangeCallback> stateChangeCallbacks = new ArrayList<StateChangeCallback>();

  public static StateMachine getInstance() {
    if (instance == null) {
      instance = new StateMachine();
    }
    return instance;
  }

  public StateMachine() {
    uiHandler = new Handler(Looper.getMainLooper());
    stageVoiceMap.put(STATE_NONE, "Cognitive assistant begin. Now please look at AED directly.");
    stageVoiceMap.put(STATE_AED_FOUND, "Please turn on AED.");
    stageVoiceMap.put(STATE_AED_ON, "Nice! Now apply pad and plug in");
    stageVoiceMap.put(STATE_AED_PLUGIN, "Congratulations, now wait for further instructions");
    stageVoiceMap.put(STATE_AED_SHOCK, "Press orange button to deliver shock");
  }

  public void updateState(int currentState) {
    if (state != currentState) {
      broadcastStateChange(state, currentState);
    }
    state = currentState;
  }

  public String getVoiceInstruction(int currentState) {
    if (stageVoiceMap.containsKey(currentState)) {
      return stageVoiceMap.get(currentState);
    }
    return "";
  }

  public void registerStateChangeCallback(StateChangeCallback callback) {
    this.stateChangeCallbacks.add(callback);
  }

  public void broadcastStateChange(final int prevState, final int currentState) {
    uiHandler.post(new Runnable() {
      @Override public void run() {
        for (StateChangeCallback callback : stateChangeCallbacks) {
          callback.onChange(prevState, currentState);
        }
      }
    });
  }
}
