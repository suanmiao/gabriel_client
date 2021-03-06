package edu.cmu.cs.gabriel;

import java.io.File;

import android.os.Environment;

public class Const {

  // whether to use real-time captured images or load images from files for testing
  public static final boolean LOAD_IMAGES = false;

  /************************ In both demo and experiment mode *******************/
  // directory for all application related files (input + output)
  public static final File ROOT_DIR = new File(Environment.getExternalStorageDirectory() +
      File.separator + "Gabriel" + File.separator);

  // image size and frame rate
  public static final int MIN_FPS = 15;
  // options: 320x180, 640x360, 1280x720, 1920x1080
  public static final int IMAGE_WIDTH = 640;
  public static final int IMAGE_HEIGHT = 360;

  // port protocol to the server
  public static final int VIDEO_STREAM_PORT = 9098;
  public static final int ACC_STREAM_PORT = 9099;
  public static final int RESULT_RECEIVING_PORT = 9101;

  // load images (JPEG) from files and pretend they are just captured by the camera
  public static final String APP_NAME = "pingpong";
  public static final File TEST_IMAGE_DIR = new File(ROOT_DIR.getAbsolutePath() +
      File.separator + "images-" + APP_NAME + File.separator);

  /************************ Demo mode only *************************************/
  // server IP
   public static final String SERVER_IP = "128.2.211.75";  // Cloudlet
// public static final String SERVER_IP = "128.2.209.246";  // Cloudlet

  //public static final String SERVER_IP = "128.237.187.96";  // Local

  // token size
  public static final int TOKEN_SIZE = 3;

  /************************ Experiment mode only *******************************/
  // server IP list
  public static final String[] SERVER_IP_LIST = {
      "128.2.213.106",
  };

  // token size list
  public static final int[] TOKEN_SIZE_LIST = { 1 };

  // maximum times to ping (for time synchronization
  public static final int MAX_PING_TIMES = 20;

  // a small number of images used for compression (bmp files), usually a subset of test images
  // these files are loaded into memory first so cannot have too many of them!
  public static final File COMPRESS_IMAGE_DIR = new File(ROOT_DIR.getAbsolutePath() +
      File.separator + "images-" + APP_NAME + "-compress" + File.separator);
  // the maximum allowed compress images to load
  public static final int MAX_COMPRESS_IMAGE = 3;

  // result file
  public static final File EXP_DIR = new File(ROOT_DIR.getAbsolutePath() + File.separator + "exp");
}