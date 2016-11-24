package edu.cmu.cs.gabriel.token;

import android.os.Handler;
import android.os.Message;
import edu.cmu.cs.gabriel.network.NetworkProtocol;
import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.ConcurrentHashMap;

public class TokenController {
  private static final String LOG_TAG = "TokenController";

  // the number of tokens remained
  private int currentToken = 0;

  // information about all sent packets, the key is the frameID and the value documents relevant timestamps
  private ConcurrentHashMap<Long, SentPacketInfo> sentPackets =
      new ConcurrentHashMap<Long, SentPacketInfo>();

  private Object tokenLock = new Object();

  private FileWriter fileWriter = null;

  // timestamp when the last ACK was received
  private long prevRecvFrameID = 0;

  public TokenController(int tokenSize, File resultSavingPath) {
    this.currentToken = tokenSize;
  }

  public Handler tokenHandler = new Handler() {

    public void handleMessage(Message msg) {
      if (msg.what == NetworkProtocol.NETWORK_RET_SYNC) {
      }
      if (msg.what == NetworkProtocol.NETWORK_RET_TOKEN) {
        ReceivedPacketInfo receivedPacket = (ReceivedPacketInfo) msg.obj;
        long recvFrameID = receivedPacket.frameID;
        String recvEngineID = receivedPacket.engineID;

        // increase appropriate amount of tokens
        long increaseCount = 0;
        for (long frameID = prevRecvFrameID + 1; frameID < recvFrameID; frameID++) {
          SentPacketInfo sentPacket = null;
          sentPacket = sentPackets.remove(frameID);
          if (sentPacket != null) {
            increaseCount++;
          }
        }
        increaseTokens(increaseCount);

        // deal with the current response
        SentPacketInfo sentPacket = sentPackets.get(recvFrameID);
        if (sentPacket != null) {
          // do not increase token if have already received duplicated ack
          if (recvFrameID > prevRecvFrameID) {
            increaseTokens(1);
          }
        }
        prevRecvFrameID = recvFrameID;
      }
    }
  };

  public void logSentPacket(long frameID, long dataTime, long compressedTime) {
    this.sentPackets.put(frameID, new SentPacketInfo(dataTime, compressedTime));
  }

  /**
   * Blocks and only returns when token > 0
   *
   * @return the current token number
   */
  public int getCurrentToken() {
    synchronized (tokenLock) {
      if (this.currentToken > 0) {
        return this.currentToken;
      } else {
        try {
          tokenLock.wait();
        } catch (InterruptedException e) {
        }
        return this.currentToken;
      }
    }
  }

  public void increaseTokens(long count) {
    synchronized (tokenLock) {
      this.currentToken += count;
      this.tokenLock.notify();
    }
  }

  public void decreaseToken() {
    synchronized (tokenLock) {
      if (this.currentToken > 0) {
        this.currentToken--;
      }
      this.tokenLock.notify();
    }
  }

  public void close() {
    sentPackets.clear();
  }
}
