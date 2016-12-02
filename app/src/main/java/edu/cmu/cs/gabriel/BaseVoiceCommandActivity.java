package edu.cmu.cs.gabriel;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import com.google.android.glass.view.WindowUtils;

public class BaseVoiceCommandActivity extends Activity {

  private static final String LOG_TAG = "Main";

  protected SpeechHelper speechHelper;

  @Override protected void onCreate(Bundle savedInstanceState) {
    Log.v(LOG_TAG, "++onCreate");
    super.onCreate(savedInstanceState);
    getWindow().requestFeature(WindowUtils.FEATURE_VOICE_COMMANDS);
    setContentView(R.layout.activity_new);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED +
        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON +
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    speechHelper = new SpeechHelper(this);
  }

  @Override public boolean onCreatePanelMenu(int featureId, Menu menu) {
    if (isMyMenu(featureId)) {
      getMenuInflater().inflate(R.menu.instruction, menu);
      return true;
    }
    return super.onCreatePanelMenu(featureId, menu);
  }

  @Override public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.instruction, menu);
    return true;
  }

  @Override public boolean onPreparePanel(int featureId, View view, Menu menu) {
    if (isMyMenu(featureId)) {
      return true;
    }
    return super.onPreparePanel(featureId, view, menu);
  }

  @Override public boolean onMenuItemSelected(int featureId, MenuItem item) {
    if (isMyMenu(featureId)) {
      switch (item.getItemId()) {
        case R.id.instruction_yes:
          return true;
        case R.id.instruction_no:
          return true;
      }
    }
    return super.onMenuItemSelected(featureId, item);
  }

  @Override public void onPanelClosed(int featureId, Menu menu) {
    super.onPanelClosed(featureId, menu);
    if (isMyMenu(featureId)) {
      // When the menu panel closes, either an item is selected from the menu or the
      // menu is dismissed by swiping down. Either way, we end the activity.
    }
  }

  /**
   * Returns {@code true} when the {@code featureId} belongs to the options menu or voice
   * menu that are controlled by this menu activity.
   */
  private boolean isMyMenu(int featureId) {
    return featureId == Window.FEATURE_OPTIONS_PANEL
        || featureId == WindowUtils.FEATURE_VOICE_COMMANDS;
  }
}
