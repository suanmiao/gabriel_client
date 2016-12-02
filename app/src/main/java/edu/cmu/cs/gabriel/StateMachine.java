package edu.cmu.cs.gabriel;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
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
  public static final int STATE_AED_FINISH = 4;

  public static final int TIMEOUT_NONE = -100;

  private int aed_state = -2;
  private int timeout_state = TIMEOUT_NONE;
  private boolean aed_found = false;
  private boolean yellow_flash = false;
  private boolean yellow_plug = false;
  private boolean orange_flash = false;

  private int token_size = 0;

  private Handler uiHandler;

  public static class StateModel {
    public int aed_state = STATE_NONE;
    public int timeout_state = TIMEOUT_NONE;
    public boolean aed_found = false;
    public boolean yellow_flash = false;
    public boolean yellow_plug = false;
    public boolean orange_flash = false;
  }

  public interface StateChangeCallback {
    void onChange(int prevState, int currentState);
  }

  public List<StateChangeCallback> aedStateChangeCallbacks = new ArrayList<StateChangeCallback>();
  public List<StateChangeCallback> timeoutStateChangeCallbacks =
      new ArrayList<StateChangeCallback>();
  public List<StateChangeCallback> aedFoundStateChangeCallbacks =
      new ArrayList<StateChangeCallback>();
  public List<StateChangeCallback> yellowFlashStateChangeCallbacks =
      new ArrayList<StateChangeCallback>();
  public List<StateChangeCallback> yellowPlugStateChangeCallbacks =
      new ArrayList<StateChangeCallback>();
  public List<StateChangeCallback> orangeFlashStateChangeCallbacks =
      new ArrayList<StateChangeCallback>();
  public List<StateChangeCallback> tokenSizeChangeCallbacks = new ArrayList<StateChangeCallback>();

  public static StateMachine getInstance() {
    if (instance == null) {
      instance = new StateMachine();
    }
    return instance;
  }

  public StateMachine() {
    uiHandler = new Handler(Looper.getMainLooper());
  }

  public void updateState(StateModel model) {
    if (aed_state != model.aed_state) {
      int prev_value = aed_state;
      aed_state = model.aed_state;
      broadcastStateChange(prev_value, model.aed_state, aedStateChangeCallbacks);
    }

    if (timeout_state != model.timeout_state) {
      int prev_value = timeout_state;
      timeout_state = model.timeout_state;
      broadcastStateChange(prev_value, model.timeout_state, timeoutStateChangeCallbacks);
    }

    if (aed_found != model.aed_found) {
      boolean prev_value = orange_flash;
      aed_found = model.aed_found;
      broadcastStateChange(prev_value ? 1 : 0, model.aed_found ? 1 : 0,
          aedFoundStateChangeCallbacks);
    }

    if (yellow_flash != model.yellow_flash) {
      boolean prev_value = orange_flash;
      yellow_flash = model.yellow_flash;
      broadcastStateChange(prev_value ? 1 : 0, model.yellow_flash ? 1 : 0,
          yellowFlashStateChangeCallbacks);
    }

    if (yellow_plug != model.yellow_plug) {
      boolean prev_value = orange_flash;
      yellow_plug = model.yellow_plug;
      broadcastStateChange(prev_value ? 1 : 0, model.yellow_plug ? 1 : 0,
          yellowPlugStateChangeCallbacks);
    }

    if (orange_flash != model.orange_flash) {
      boolean prev_value = orange_flash;
      orange_flash = model.orange_flash;
      broadcastStateChange(prev_value ? 1 : 0, model.orange_flash ? 1 : 0,
          orangeFlashStateChangeCallbacks);
    }
  }

  public void updateTokenSize(int token_size) {
    if (this.token_size != token_size) {
      int prev_value = this.token_size;
      this.token_size = token_size;
      broadcastStateChange(prev_value, token_size, tokenSizeChangeCallbacks);
    }
  }

  public void registerAEDStateChangeCallback(StateChangeCallback callback) {
    this.aedStateChangeCallbacks.add(callback);
  }

  public void registerTimeoutStateChangeCallback(StateChangeCallback callback) {
    this.timeoutStateChangeCallbacks.add(callback);
  }

  public void registerAEDFoundStateChangeCallback(StateChangeCallback callback) {
    this.aedFoundStateChangeCallbacks.add(callback);
  }

  public void registerYellowFlashStateChangeCallback(StateChangeCallback callback) {
    this.yellowFlashStateChangeCallbacks.add(callback);
  }

  public void registerYellowPlugStateChangeCallback(StateChangeCallback callback) {
    this.yellowPlugStateChangeCallbacks.add(callback);
  }

  public void registerOrangeFlashStateChangeCallback(StateChangeCallback callback) {
    this.orangeFlashStateChangeCallbacks.add(callback);
  }

  public void registerTokenSizeChangeCallback(StateChangeCallback callback) {
    this.tokenSizeChangeCallbacks.add(callback);
  }

  public void broadcastStateChange(final int prevState, final int currentState,
      final List<StateChangeCallback> stateChangeCallbacks) {
    uiHandler.post(new Runnable() {
      @Override public void run() {
        for (StateChangeCallback callback : stateChangeCallbacks) {
          callback.onChange(prevState, currentState);
        }
      }
    });
  }

  public int getAed_state() {
    return aed_state;
  }

  public int getTimeout_state() {
    return timeout_state;
  }

  public boolean isYellow_flash() {
    return yellow_flash;
  }

  public boolean isAed_found() {
    return aed_found;
  }

  public boolean isOrange_flash() {
    return orange_flash;
  }

  public boolean isYellow_plug() {
    return yellow_plug;
  }

  public int getToken_size() {
    return token_size;
  }
}
