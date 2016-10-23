package edu.cmu.cs.gabriel;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by suanmiao on 10/22/16.
 */
public class GlobalCache {

  public static GlobalCache instance;

  public static GlobalCache getInstance() {
    if (instance == null) {
      instance = new GlobalCache();
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

  public GlobalCache() {
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
  }
}
