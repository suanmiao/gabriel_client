package edu.cmu.cs.gabriel;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

public class ConfigActivity extends Activity {

  private static final String LOG_TAG = "Config";
  private Button ensureButton;
  private EditText inputText;
  public static final String KEY_IP = "ip";

  @Override protected void onCreate(Bundle savedInstanceState) {
    Log.v(LOG_TAG, "++onCreate");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_config);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED +
        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON +
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    initWidget();
  }

  private void initWidget() {
    ensureButton = (Button) findViewById(R.id.config_confirm);
    inputText = (EditText) findViewById(R.id.config_input);
    ensureButton.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        Intent intent = new Intent(ConfigActivity.this, GabrielClientActivity.class);
        intent.putExtra(KEY_IP, inputText.getText().toString());
        startActivity(intent);
        finish();
      }
    });
  }
}
