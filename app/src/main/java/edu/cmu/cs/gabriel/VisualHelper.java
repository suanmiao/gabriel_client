package edu.cmu.cs.gabriel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.util.DisplayMetrics;
import android.util.Log;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by melanieseah on 5/1/17.
 */
public class VisualHelper {

    private int h, w;
    private double axisEpsilon, greenEpsilon, minGreenArea;
    private double minAedArea;
    private double maxOrangeArea, minOrangeArea, orangeEpsilon;
    private boolean showAed = true, showGreen = false, showOrange = false, showConn = false;

    private Rect defaultRect = new Rect(0,0,0,0);
    private RotatedRect defaultRotatedRect = new RotatedRect(new Point(0,0), new Size(0,0), (double) 0);

    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
            Log.e("***", "OpenCV didn't work");
        } else {
            Log.e("~~~", "OpenCV worked");
        }
    }

    public VisualHelper (Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        // Set up epsilon values for each function
        setupEpsilons(metrics.widthPixels, metrics.heightPixels);
    }

    private void setupEpsilons(int w, int h) {
        if (this.w != w || this.h != h) {
            this.w = w;
            this.h = h;
            int totalArea = this.w * this.h;
            this.axisEpsilon = this.h / 100.0;
            this.greenEpsilon = this.h / 3.0;
            this.minGreenArea = totalArea * 0.0005;
            this.minAedArea = totalArea * 0.05;
            this.maxOrangeArea = totalArea * 0.008;
            this.minOrangeArea = totalArea * 0.0005;
            this.orangeEpsilon = this.h / 40.0;
        }
    }

    public Bitmap greenWrapper(Bitmap original) {
        int w = original.getWidth();
        int h = original.getHeight();
        setupEpsilons(w, h);
        Mat img = new Mat(h, w, CvType.CV_8UC1);
        Utils.bitmapToMat(original, img);
        // Preprocess image
        Imgproc.blur(img, img, new Size(17,17));
        Imgproc.cvtColor(img, img, Imgproc.COLOR_RGB2HSV);
        Rect aedRect = findAed(img);
        if (aedRect == this.defaultRect) {
            return original;
        }
        //RotatedRect greenBtn = findGreenButton(img, aedRect);
        if (showAed) {
            Mat altMat = new Mat(h, w, CvType.CV_8UC1);
            Bitmap altBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Utils.bitmapToMat(original, altMat);
            Imgproc.rectangle(altMat, new Point(aedRect.x, aedRect.y),
                    new Point(aedRect.width+aedRect.x, aedRect.height+aedRect.y),
                    new Scalar(255, 255, 255), 2);
            Utils.matToBitmap(altMat, altBmp);
            return altBmp;
        } else {
            return original;
        }
    }

    // Finds red section of AED
    private Rect findAed(Mat img) {
        // Threshold and get contours
        Mat mask = new Mat(this.h, this.w, CvType.CV_8UC1);
        Mat output = new Mat(this.h, this.w, CvType.CV_8UC1);
        Core.inRange(img, new Scalar(80, 170, 120), new Scalar(200, 255,250), mask);
        Core.bitwise_and(img, img, output, mask);
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(mask.clone(), contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Find biggest contour
        MatOfPoint c, maxContour = null;
        double a;
        double maxArea = this.minAedArea;
        for (int i = 0; i < contours.size(); i++) {
            c = contours.get(i);
            a = Imgproc.contourArea(c);
            if (a > maxArea) {
                maxArea = a;
                maxContour = c;
            }
        }

        if (maxContour == null) {
            return defaultRect;
        } else {
            return Imgproc.boundingRect(maxContour);
        }
    }

    private RotatedRect findGreenButton(Mat img, Rect rect) {
        double x1 = rect.x;
        double y1 = rect.y;
        Size s = rect.size();
        double w = s.width;
        double h = s.height;
        double x2 = x1 + w;
        double y2 = y1 + h;

        // Threshold and get contours
        Mat mask = new Mat(this.h, this.w, CvType.CV_8UC1);
        Mat output = new Mat(this.h, this.w, CvType.CV_8UC1);
        Core.inRange(img, new Scalar(4, 35, 77), new Scalar(100, 100, 200), mask);
        Core.bitwise_and(img, img, output, mask);
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(mask.clone(), contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Find biggest contour
        MatOfPoint c, maxContour = null;
        MatOfPoint2f c2f;
        double a;
        double maxArea = this.minAedArea;
        RotatedRect ell;
        int num;
        for (int i = 0; i < contours.size(); i++) {
            c = contours.get(i);
            a = Imgproc.contourArea(c);
            c2f = new MatOfPoint2f(c.toArray());
            num = c2f.toList().size();
            if (num >= 5 && a > maxArea) {
                ell = Imgproc.fitEllipse(c2f);
            }
        }

        return defaultRotatedRect;
    }


}
