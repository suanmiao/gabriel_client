package edu.cmu.cs.gabriel;

import android.widget.TextView;

/**
 * Created by suanmiao on 01/12/2016.
 */

public class Util {
  public enum AED_STATE {
    STATE_NONE("Cognitive assistant begin. Now please look at AED directly.",
        "State 0 time out, please make sure you are looking at AED", "INITIALIZED"),
    STATE_AED_FOUND("Please turn on AED.", "State 1 timeout, please make sure the AED is turned on",
        "AED FOUND"),
    STATE_AED_ON("Nice! Now apply pad and plug in",
        "State 2 timeout, please make sure the yellow connector is plugged in", "AED ON"),
    STATE_AED_PLUGIN("Congratulations, now wait for further instructions",
        "State 2 timeout, please make sure the patient is shockable", "YELLOW PLUGGED"),
    STATE_AED_SHOCK("Press orange button to deliver shock",
        "State 3 timeout, please make sure you have delivered the shock", "SHOCKING"),
    STATE_AED_FINISH("The instruction finishes", "State 4 timeout, it's fine", "FINISH");
    private String state_text;
    private String timeout_text;
    private String display_text;

    AED_STATE(String state_text, String timeout_text, String display_text) {
      this.state_text = state_text;
      this.timeout_text = timeout_text;
      this.display_text = display_text;
    }

    public String getStateText() {
      return this.state_text;
    }

    public String getTimeout_text() {
      return timeout_text;
    }

    public String getDisplay_text() {
      return display_text;
    }
  }

  public static void bindStateText(SListAdapter adapter, int state) {
    String stateText = getAEDState(state).getStateText();
    adapter.addItem(new SListAdapter.Model(stateText));
  }

  public static void bindTimeoutText(SListAdapter adapter, int state) {
    String timeoutText = getAEDState(state).getTimeout_text();
    adapter.addItem(new SListAdapter.Model(timeoutText));
  }

  public static AED_STATE getAEDState(int state) {
    switch (state) {
      case StateMachine.AED_FOUND:
        return AED_STATE.STATE_AED_FOUND;
      case StateMachine.AED_ON:
        return AED_STATE.STATE_AED_ON;
      case StateMachine.AED_PLUGIN:
        return AED_STATE.STATE_AED_PLUGIN;
      case StateMachine.AED_SHOCK:
        return AED_STATE.STATE_AED_SHOCK;
      case StateMachine.AED_FINISH:
        return AED_STATE.STATE_AED_FINISH;
      default:
        return AED_STATE.STATE_NONE;
    }
  }

  public static void bindOverallState(TextView textOverall) {
    StateMachine machine = StateMachine.getInstance();
    String aedState = getAEDState(machine.model.aed_state).getDisplay_text();
    String timeoutState = "";
    if (machine.model.timeout_state != StateMachine.TIMEOUT_NONE) {
      timeoutState = getAEDState(machine.model.timeout_state).getDisplay_text();
    }
    String text = aedState + "\n" + timeoutState;
    textOverall.setText(text);
  }

  public static void bindDetailState(TextView textDetail) {
    StateMachine machine = StateMachine.getInstance();
    String text = "";
//    text += (generateText("AED", machine.model.continuous_aed) + "\n");
//    text += (generateText("Plug", machine.model.continuous_yellow_plug) + "\n");
//    text += (generateText("Y Flash", false) + "\n");
//    text += (generateText("O Flash", machine.model.continuous_orange_flash) + "\n");
//    text += ("token size: " + machine.token_size + "\n");
//    text += (generateText("In Frame", machine.model.frame_aed) + "\n");
    textDetail.setText(text);
  }

  public static String generateText(String key, boolean value) {
    return key + ": " + (value ? "Found" : "");
  }
}
