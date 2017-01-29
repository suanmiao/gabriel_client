package edu.cmu.cs.gabriel;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import org.greenrobot.eventbus.EventBus;

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

  public StateModel model;

  public int token_size = 0;

  private Handler uiHandler;

  public static class StateUpdateEvent {
    public enum Field {
      AED_STATE,
      TIMEOUT_STATE,
      FRAME_AED,
      FRAME_YELLOW_PLUG,
      FRAME_ORANGE_FLASH,
      CONTINUOUS_AED,
      CONTINUOUS_YELLOW_PLUG,
      CONTINUOUS_ORANGE_FLASH,
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

  //'aed_state': state_machine.ret_state,
  //'timeout_sate': state_machine.ret_timeout_state,
  //
  //'frame_aed': state_machine.frame_aed_exist,
  //'frame_aed_box': state_machine.frame_aed_box.tolist(),
  //'frame_yellow_plug': state_machine.frame_yellow_plug,
  //'frame_yellow_plug_box': state_machine.frame_yellow_plug_box.tolist(),
  //'frame_orange_flash': state_machine.frame_orange_flash,
  //'frame_orange_flash_box': state_machine.frame_orange_flash_box.tolist(),
  //
  //'continuous_aed': state_machine.continuous_aed_exist,
  //'continuous_yellow_plug': state_machine.continuous_yellow_plug,
  //'continuous_orange_flash': state_machine.continuous_orange_flash,

  public static class StateModel {
    public long frameId = -1;
    public int aed_state = STATE_NONE;
    public int timeout_state = TIMEOUT_NONE;

    public boolean frame_aed = false;
    public List<Float> frame_aed_box;

    public boolean frame_yellow_plug = false;
    public List<Float> frame_yellow_plug_box;

    public boolean frame_orange_flash = false;
    public List<Float> frame_orange_flash_box;

    public boolean continuous_aed = false;
    public boolean continuous_yellow_plug = false;
    public boolean continuous_orange_flash = false;
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

  public void updateState(StateModel model) {
    StateUpdateEvent event = new StateUpdateEvent(this.model, model);
    if (model.aed_state != this.model.aed_state) {
      event.addField(StateUpdateEvent.Field.AED_STATE);
    }
    if (model.timeout_state != this.model.timeout_state) {
      event.addField(StateUpdateEvent.Field.TIMEOUT_STATE);
    }
    if (model.frame_aed != this.model.frame_aed) {
      event.addField(StateUpdateEvent.Field.FRAME_AED);
    }
    if (model.frame_orange_flash != this.model.frame_orange_flash) {
      event.addField(StateUpdateEvent.Field.FRAME_ORANGE_FLASH);
    }
    if (model.frame_yellow_plug != this.model.frame_yellow_plug) {
      event.addField(StateUpdateEvent.Field.FRAME_YELLOW_PLUG);
    }
    if (model.continuous_aed != this.model.continuous_aed) {
      event.addField(StateUpdateEvent.Field.CONTINUOUS_AED);
    }
    if (model.continuous_orange_flash != this.model.continuous_orange_flash) {
      event.addField(StateUpdateEvent.Field.CONTINUOUS_ORANGE_FLASH);
    }
    if (model.continuous_yellow_plug != this.model.continuous_yellow_plug) {
      event.addField(StateUpdateEvent.Field.CONTINUOUS_YELLOW_PLUG);
    }
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
