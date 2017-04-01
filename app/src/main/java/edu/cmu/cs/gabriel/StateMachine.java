package edu.cmu.cs.gabriel;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.greenrobot.eventbus.EventBus;

/**
 * Created by suanmiao on 23/11/2016.
 */

public class StateMachine {

  public static StateMachine instance;
  public static final int AED_NONE = -1;
  public static final int AED_FOUND = 0;
  public static final int AED_ON = 1;
  public static final int AED_PLUGIN = 2;
  public static final int AED_SHOCK = 3;
  public static final int AED_FINISH = 4;

  public static final int PAD_NONE = -11;
  public static final int PAD_DETECT_AGE = -10;
  public static final int PAD_AGE_CONFIRM = -9;
  public static final int PAD_CORRECT_PAD = -8;
  public static final int PAD_PEEL_PAD =  -7;
  public static final int PAD_LEFT_PAD_SHOW = -6;
  public static final int PAD_WAIT_LEFT_PAD = -5;
  public static final int PAD_LEFT_PAD = -4;
  public static final int PAD_WAIT_RIGHT_PAD = -3;
  public static final int PAD_RIGHT_PAD = -2;

  public static final int RESP_AGE_DETECT_YES = 1;
  public static final int RESP_AGE_DETECT_NO = 2;
  public static final int RESP_PEEL_PAD_LEFT = 3;
  public static final int RESP_PEEL_PAD_RIGHT = 7;
  public static final int RESP_LEFT_PAD_FINISHED = 4;
  public static final int RESP_RIGHT_PAD_FINISHED = 5;
  public static final int RESP_PAD_APPLYING_FINISHED = 8;
  public static final int RESP_START_DETECTION = 6;

  public static final int TIMEOUT_NONE = -100;

  public StateModel model;

  public int token_size = 0;

  private Handler uiHandler;

  public static String getStateStrByNum(int num){
    String str = "none";
      switch (num){
        case AED_NONE:
          str = "AED_NONE";
          break;
        case AED_FOUND:
          str = "AED_FOUND";
          break;
        case AED_ON:
          str = "AED_ON";
          break;
        case AED_PLUGIN:
          str = "AED_PLUGIN";
          break;
        case AED_SHOCK:
          str = "AED_SHOCK";
          break;
        case AED_FINISH:
          str = "AED_FINISH";
          break;
        case PAD_NONE:
          str = "PAD_NONE";
          break;
        case PAD_AGE_CONFIRM:
          str = "PAD_AGE_CONFIRM";
          break;
        case PAD_CORRECT_PAD:
          str = "PAD_CORRECT_PAD";
          break;
        case PAD_PEEL_PAD:
          str = "PAD_PEEL_PAD";
          break;
        case PAD_LEFT_PAD_SHOW:
          str = "PAD_LEFT_PAD_SHOW";
          break;
        case PAD_LEFT_PAD:
          str = "PAD_LEFT_PAD";
          break;
        case PAD_WAIT_LEFT_PAD:
          str = "PAD_WAIT_LEFT_PAD";
          break;
        case PAD_WAIT_RIGHT_PAD:
          str = "PAD_WAIT_RIGHT_PAD";
          break;
        case PAD_RIGHT_PAD:
          str = "PAD_RIGHT_PAD";
          break;
      }
    return str;
  }

  public static String getRespStrByNum(int num){
    String str = "none";
    switch (num) {
        case RESP_AGE_DETECT_YES:
          str = "RESP_AGE_DETECT_YES";
          break;
        case RESP_AGE_DETECT_NO:
          str = "RESP_AGE_DETECT_NO";
          break;
        case RESP_PEEL_PAD_LEFT:
          str = "RESP_PEEL_PAD_LEFT";
          break;
        case RESP_PEEL_PAD_RIGHT:
          str = "RESP_PEEL_PAD_RIGHT";
          break;
        case RESP_LEFT_PAD_FINISHED:
          str = "RESP_LEFT_PAD_FINISHED";
          break;
        case RESP_RIGHT_PAD_FINISHED:
          str = "RESP_RIGHT_PAD_FINISHED";
          break;
        case RESP_PAD_APPLYING_FINISHED:
          str = "RESP_PAD_APPLYING_FINISHED";
          break;
        case RESP_START_DETECTION:
          str = "RESP_START_DETECTION";
          break;
        case -1:
          str = "default value";
    }
    return str;
  }


  public static class StateUpdateEvent {

    public enum Field {
      AED_STATE,
      TIMEOUT_STATE,
      FRAME_AED,
      FRAME_ORANGE_BTN,
      FRAME_YELLOW_PLUG,
      FRAME_ORANGE_FLASH,
      FRAME_JOINTS,
      PAD_Adult,
      PAD_WRONG_PAD,
      PAD_WRONG_LEFT,
      PAD_DETECT,
      PAITIENT_IS_ADULT
    }

    public List<Field> updateField = new ArrayList<Field>();
    public StateModel prevModel;
    public StateModel currentModel;

    public void addField(Field field) {
      updateField.add(field);
    }

    public StateUpdateEvent(StateModel prevModel, StateModel currentModel) {
      this.prevModel = prevModel;
      this.currentModel = currentModel;
    }
  }

  public static class StateModel {

    public long frameId = -1;
    public int aed_state = AED_NONE;
    public int timeout_state = TIMEOUT_NONE;

    public boolean frame_aed = false;
    public List<Float> frame_aed_box;

    public boolean frame_orange_btn = false;
    public List<Float> frame_orange_btn_box;

    public boolean frame_yellow_plug = false;
    public List<Float> frame_yellow_plug_box;

    public boolean frame_orange_flash = false;
    public List<Float> frame_orange_flash_box;

    public boolean frame_joints = false;
    public List<List<Float>> joints;

    public int pad_adult = -1;
    public boolean pad_wrong_pad = false;
    public boolean pad_wrong_left = false;
    public int[] pad_detect = new int[2];
    public int patient_is_adult = -1;

    @Override
    public String toString() {
      Log.e("aed","frame "+frameId);
      StringBuilder sb = new StringBuilder();
      sb.append("aed: ");
      String str = "";
      if(frame_aed) {
        for (Float f : frame_aed_box) {
          str += String.valueOf(f);
          str += " ";
        }
      }
      sb.append(str);
      return sb.toString();
    }
  }

  public static StateMachine getInstance() {
    if (instance == null) {
      instance = new StateMachine();
    }
    return instance;
  }

  public StateMachine() {
    uiHandler = new Handler(Looper.getMainLooper());
    model = new StateModel();
    model.aed_state = -1;
    model.timeout_state = TIMEOUT_NONE;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("model " + this.model.toString());
    return sb.toString();
  }

  public void updateState(StateModel model) {
    StateUpdateEvent event = new StateUpdateEvent(this.model, model);
    if (model.aed_state != this.model.aed_state) {
      event.addField(StateUpdateEvent.Field.AED_STATE);
    }
    if (model.timeout_state != this.model.timeout_state) {
      event.addField(StateUpdateEvent.Field.TIMEOUT_STATE);
    }

      event.addField(StateUpdateEvent.Field.PAD_Adult);
      event.addField(StateUpdateEvent.Field.PAD_DETECT);
      event.addField(StateUpdateEvent.Field.PAD_WRONG_LEFT);
      event.addField(StateUpdateEvent.Field.PAD_WRONG_PAD);
      event.addField(StateUpdateEvent.Field.PAITIENT_IS_ADULT);

      event.addField(StateUpdateEvent.Field.FRAME_AED);
      event.addField(StateUpdateEvent.Field.FRAME_ORANGE_BTN);
      event.addField(StateUpdateEvent.Field.FRAME_ORANGE_FLASH);
      event.addField(StateUpdateEvent.Field.FRAME_YELLOW_PLUG);
      event.addField(StateUpdateEvent.Field.FRAME_JOINTS);


    if(event.updateField.size() > 0){
      EventBus.getDefault().post(event);
    }
    this.model = model;
  }

  public void updateTokenSize(int token_size) {
    if (this.token_size != token_size) {
      this.token_size = token_size;
    }
  }
}
