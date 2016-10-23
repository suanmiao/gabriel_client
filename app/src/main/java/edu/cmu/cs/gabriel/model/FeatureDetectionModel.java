package edu.cmu.cs.gabriel.model;

import android.graphics.Rect;
import java.util.List;

/**
 * Created by suanmiao on 10/14/16.
 */
public class FeatureDetectionModel {
  public String feature;
  public float confidence;
  public List<Float> area;

  public Rect getRect() {
    float left = area.get(0);
    float top = area.get(1);
    float right = area.get(2);
    float bottom = area.get(3);
    return new Rect((int)left, (int)top, (int)right, (int)bottom);
  }
}
