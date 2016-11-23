package edu.cmu.cs.gabriel;

import android.app.ActivityManager;
import android.content.Context;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by suanmiao on 10/22/16.
 */
public class GlobalCache {

  public static GlobalCache instance;

  public static int MAX_FRAME_CNT = 100;

  public static GlobalCache getInstance(Context context) {
    if (instance == null) {
      instance = new GlobalCache(context);
    }
    return instance;
  }

  public static class FrameHolder {
    public long id;
    public byte[] data;

    public FrameHolder(long id, byte[] data) {
      this.id = id;
      this.data = data;
    }
  }

  public Queue<FrameHolder> frameHolderQueue;
  public HashMap<Long, FrameHolder> frameHolderHashMap;

  public GlobalCache(Context context) {
    ActivityManager actManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
    actManager.getMemoryInfo(memInfo);
    long totalMemory = memInfo.totalMem;
    if(totalMemory < 710217984){
      MAX_FRAME_CNT = 20;
    }
    frameHolderQueue = new LinkedList<FrameHolder>();
    frameHolderHashMap = new HashMap<Long, FrameHolder>();
  }

  public FrameHolder getFrameById(long id) {
    return frameHolderHashMap.get(id);
  }

  public void clearOldItems(long id) {
    while (frameHolderQueue.size() > 0 && frameHolderQueue.peek().id < id) {
      FrameHolder holder = frameHolderQueue.poll();
      frameHolderHashMap.remove(holder.id);
    }
  }

  public void enqueue(long id, byte[] data) {
    FrameHolder holder = new FrameHolder(id, data);
    frameHolderHashMap.put(id, holder);
    frameHolderQueue.add(holder);
    if (frameHolderQueue.size() == MAX_FRAME_CNT) {
      FrameHolder oldHolder = frameHolderQueue.poll();
      frameHolderHashMap.remove(oldHolder.id);
    }
  }
}
