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
    private boolean showAed = true, showGreen = true, showOrange = false, showConn = false;

    // CV parameters
    private Scalar green = new Scalar(0, 255, 0);
    private Scalar black = new Scalar(0, 0, 0);
    private Scalar blue = new Scalar(255, 0, 0);
    private Scalar white = new Scalar(255, 255, 255);
    private Scalar aedLower = new Scalar(80, 170, 120);
    private Scalar aedUpper = new Scalar(200, 255,250);
    private Size aedBlur = new Size(17,17);
    private Scalar connLower = new Scalar(10, 0, 150);
    private Scalar connUpper = new Scalar(70, 40, 240);
    private Size connBlur = new Size(9,9);
    private Scalar orangeLower = new Scalar(0, 80, 190);
    private Scalar orangeUpper = new Scalar(100, 150, 230);
    private Size orangeBlur = new Size(19,19);
    private Scalar greenLower = new Scalar(4, 35, 77);
    private Scalar greenUpper = new Scalar(100, 100, 200);

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

    public void setVisibility(boolean aed, boolean green, boolean orange, boolean conn) {
        this.showAed = aed;
        this.showGreen = green;
        this.showOrange = orange;
        this.showConn = conn;
    }

    public Bitmap orangeWrapper(Bitmap original) {
        Mat altMat;
        Bitmap altBmp;
        int w = original.getWidth();
        int h = original.getHeight();
        setupEpsilons(w, h);
        Mat blur1 = new Mat(h, w, CvType.CV_8UC1);
        Utils.bitmapToMat(original, blur1);
        // Preprocess image
        Imgproc.blur(blur1, blur1, aedBlur);
        Imgproc.cvtColor(blur1, blur1, Imgproc.COLOR_RGB2HSV);
        // Find red section of AED
        Rect aedRect = findAed(blur1);
        if (aedRect == this.defaultRect) {
            return original;
        }
        Mat blur2 = blur1.clone();
        /* Comment out because using aedBlur params results in slight less accurate detection but
         * faster processing speed
        // Preprocess image
        Imgproc.blur(blur2, blur2, orangeBlur);
        Imgproc.cvtColor(blur2, blur2, Imgproc.COLOR_RGB2HSV);
        */
        // Find orange button in AED
        RotatedRect orangeBtn = findOrangeButton(blur2, aedRect);
        if (orangeBtn == this.defaultRotatedRect) {
            return original;
        }
        // If showAed, then draw the AED's bounding box
        if (showAed) {
            altMat = new Mat(h, w, CvType.CV_8UC1);
            altBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Utils.bitmapToMat(original, altMat);
            Imgproc.rectangle(altMat, new Point(aedRect.x, aedRect.y),
                    new Point(aedRect.width+aedRect.x, aedRect.height+aedRect.y),
                    white, 2);
            Utils.matToBitmap(altMat, altBmp);
            original = altBmp;
        }
        // If showGreen, then draw the green button's bounding ellipse
        if (showOrange) {
            altMat = new Mat(h, w, CvType.CV_8UC1);
            altBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Utils.bitmapToMat(original, altMat);
            Imgproc.ellipse(altMat, orangeBtn, black, 2);
            Utils.matToBitmap(altMat, altBmp);
            original = altBmp;
        }
        return original;
    }

    public Bitmap greenWrapper(Bitmap original) {
        Mat altMat;
        Bitmap altBmp;
        int w = original.getWidth();
        int h = original.getHeight();
        setupEpsilons(w, h);
        Mat img = new Mat(h, w, CvType.CV_8UC1);
        Utils.bitmapToMat(original, img);
        // Preprocess image
        Imgproc.blur(img, img, aedBlur);
        Imgproc.cvtColor(img, img, Imgproc.COLOR_RGB2HSV);
        // Find red section of AED
        Rect aedRect = findAed(img);
        if (aedRect == this.defaultRect) {
            return original;
        }
        // Find green button in AED
        Rect greenBtn = findGreenButton(img, aedRect);
        if (greenBtn == this.defaultRect) {
            return original;
        }
        // If showAed, then draw the AED's bounding box
        if (showAed) {
            altMat = new Mat(h, w, CvType.CV_8UC1);
            altBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Utils.bitmapToMat(original, altMat);
            Imgproc.rectangle(altMat, new Point(aedRect.x, aedRect.y),
                    new Point(aedRect.width+aedRect.x, aedRect.height+aedRect.y),
                    white, 2);
            Utils.matToBitmap(altMat, altBmp);
            original = altBmp;
        }
        // If showGreen, then draw the green button's bounding ellipse
        if (showGreen) {
            altMat = new Mat(h, w, CvType.CV_8UC1);
            altBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Utils.bitmapToMat(original, altMat);
            Imgproc.rectangle(altMat, new Point(greenBtn.x, greenBtn.y),
                    new Point(greenBtn.width+greenBtn.x, greenBtn.height+greenBtn.y),
                    black, 2);
            Utils.matToBitmap(altMat, altBmp);
            original = altBmp;
        }
        return original;
    }

    // Finds red section of AED; img must be blurred
    private Rect findAed(Mat img) {
        // Threshold and get contours
        Mat mask = new Mat(this.h, this.w, CvType.CV_8UC1);
        Mat output = new Mat(this.h, this.w, CvType.CV_8UC1);
        Core.inRange(img, aedLower, aedUpper, mask);
        Core.bitwise_and(img, img, output, mask);
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(mask, contours, hierarchy,
                Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_NONE);

        // Find biggest contour
        MatOfPoint c;
        double a;
        double maxArea = this.minAedArea;
        Rect maxRect = defaultRect;
        for (int i = 0; i < contours.size(); i++) {
            c = contours.get(i);
            a = Imgproc.contourArea(c);
            if (a > maxArea) {
                maxArea = a;
                maxRect = Imgproc.boundingRect(c);
            }
        }
        return maxRect;
    }

    public Bitmap connectorWrapper(Bitmap original) {
        Mat altMat;
        Bitmap altBmp;
        int w = original.getWidth();
        int h = original.getHeight();
        setupEpsilons(w, h);
        Mat blur = new Mat(h, w, CvType.CV_8UC1);
        Utils.bitmapToMat(original, blur);
        // Preprocess image
        Imgproc.blur(blur, blur, connBlur);
        Imgproc.cvtColor(blur, blur, Imgproc.COLOR_RGB2HSV);
        // Threshold and get contours
        Mat mask = new Mat(this.h, this.w, CvType.CV_8UC1);
        Core.inRange(blur, connLower, connUpper, mask);
        // Find gray subsection of AED
        Rect gray = findSubsection(original, blur, mask);
        if (gray == this.defaultRect) {
            return original;
        }
        Rect conn = findConnector(gray, mask);
        if (conn == this.defaultRect) {
            return original;
        }
        if (showConn) {
            altMat = new Mat(h, w, CvType.CV_8UC1);
            altBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Utils.bitmapToMat(original, altMat);
            Imgproc.rectangle(altMat, new Point(conn.x, conn.y),
                    new Point(conn.width+conn.x, conn.height+conn.y),
                    white, 2);
            Utils.matToBitmap(altMat, altBmp);
            original = altBmp;
        }
        return original;
    }

    // Find gray subsection of AED; img must be blurred
    private Rect findSubsection(Bitmap original, Mat img, Mat mask) {
        Mat output = new Mat(this.h, this.w, CvType.CV_8UC1);
        Core.bitwise_and(img, img, output, mask);
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(mask, contours, hierarchy,
                Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_NONE);

        // Find biggest contour
        MatOfPoint c;
        double a;
        double minBound = this.minAedArea / 5;
        double maxArea = minBound;
        List<MatOfPoint> boundingCont = new ArrayList<MatOfPoint>();
        List<Double> boundingArea = new ArrayList<Double>();
        for (int i = 0; i < contours.size(); i++) {
            c = contours.get(i);
            a = Imgproc.contourArea(c);
            if (a > minBound) {
                boundingCont.add(c);
                boundingArea.add(a);
                if (a > maxArea) {
                    maxArea = a;
                }
            }
        }

        int size = boundingCont.size();
        if (size == 0) {
            return this.defaultRect;
        } else if (size == 1) {
            return Imgproc.boundingRect(boundingCont.get(0));
        } else {
            // Too many possible gray regions; find AED red area
            return compareGrayRed(boundingCont, boundingArea, original);
        }
    }

    // When there are too many possible gray regions found, this function returns the gray region
    // that overlaps with the red AED region as the most likely gray region
    private Rect compareGrayRed(List<MatOfPoint> contours, List<Double> areas, Bitmap original) {
        Mat aedMat = new Mat(h, w, CvType.CV_8UC1);
        Utils.bitmapToMat(original, aedMat);
        // Preprocess image
        Imgproc.blur(aedMat, aedMat, aedBlur);
        Imgproc.cvtColor(aedMat, aedMat, Imgproc.COLOR_RGB2HSV);
        Rect aed = findAed(aedMat);
        if (aed == this.defaultRect) {
            // Could not find AED; return default
            return this.defaultRect;
        }
        double aedX1 = aed.x;
        double aedY1 = aed.y;
        double aedX2 = aedX1 + aed.width;
        double aedY2 = aedY1 + aed.height;
        double minArea = aed.width * aed.height / 5;
        Rect minRect = this.defaultRect;
        Rect r;
        double mpX, mpY;
        MatOfPoint c;
        double a;
        for (int i = 0; i < contours.size(); i++) {
            c = contours.get(i);
            a = areas.get(i);
            r = Imgproc.boundingRect(c);
            // AED should overlap with midpoint of r's longer side
            if (r.width > r.height) {
                mpX = r.x + (r.width / 2);
                mpY = r.y + r.height;
            } else {
                mpX = r.x + r.width;
                mpY = r.y + (r.height / 2);
            }
            if ((a > minArea) && (aedX1 < mpX) && (mpX < aedX2)
                    && (aedY1 < mpY) && (mpY < aedY2)) {
                minRect = r;
                minArea = a;
            }
        }
        return minRect;
    }

    // Finds connector on gray area of AED; img must be blurred
    private Rect findConnector(Rect rect, Mat mask) {
        int rw = rect.width;
        int rh = rect.height;
        Mat crop = mask.submat(rect.y, rect.y+rh, rect.x, rect.x+rw);

        // Find biggest contour to get contour
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(crop, contours, hierarchy,
                Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_NONE);
        double totalArea = rect.width * rect.height / 3;
        double maxArea = 240;
        Rect maxConn = this.defaultRect;
        double upperY = rw / 8;
        double a;
        MatOfPoint c;
        Rect b;
        for (int i = 0; i < contours.size(); i++) {
            c = contours.get(i);
            a = Imgproc.contourArea(c);
            if (a > maxArea) {
                b = Imgproc.boundingRect(c);
                if (b.width*b.height < totalArea && b.y < upperY && (rw-rh)*(b.height-b.width) > 0) {
                    maxArea = a;
                    maxConn = b;
                }
            }
        }
        if (maxConn != this.defaultRect) {
            return new Rect(rect.x+maxConn.x, rect.y+maxConn.y, maxConn.width, maxConn.height);
        }
        return maxConn;
    }

    // Find orange button on AED; img must be blurred
    private RotatedRect findOrangeButton(Mat img, Rect rect) {
        double x1 = rect.x;
        double y1 = rect.y;
        double w = rect.size().width;
        double h = rect.size().height;
        double x2 = x1 + w;
        double y2 = y1 + h;
        double upperThird = y1 + (w/3);
        double lowerThird = y2 - (w/3);

        // Threshold and get contours
        Mat mask = new Mat(this.h, this.w, CvType.CV_8UC1);
        Mat output = new Mat(this.h, this.w, CvType.CV_8UC1);
        Core.inRange(img, orangeLower, orangeUpper, mask);
        Core.bitwise_and(img, img, output, mask);
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(mask, contours, hierarchy,
                Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_NONE);

        // Find biggest contour
        MatOfPoint c;
        MatOfPoint2f c2f;
        double a;
        double maxArea = this.minOrangeArea;
        RotatedRect e;
        RotatedRect maxEllipse = this.defaultRotatedRect;
        int num;
        for (int i = 0; i < contours.size(); i++) {
            c = contours.get(i);
            c2f = new MatOfPoint2f(c.toArray());
            num = c2f.toList().size();
            if (num >= 5) {
                a = Imgproc.contourArea(c);
                e = Imgproc.fitEllipse(c2f);
                if ((a > maxArea)
                        && orangeCheck(e, x1, y1, x2, y2, a)){
                    if ((upperThird < e.center.y) && (e.center.y < lowerThird)) {
                        continue;
                    }
                    maxArea = a;
                    maxEllipse = e;
                }
            }
        }
        return maxEllipse;
    }

    private boolean orangeCheck(RotatedRect e, double rx1, double ry1, double rx2, double ry2, double a) {
        double eArea = (e.size.width/2) * (e.size.height/2) * Math.PI;
        return ((rx1 < e.center.x) && (ry1 <= e.center.y)
            && (e.center.x < rx2) && (e.center.y <= ry2)
            && (a >= eArea / 5) && (Math.abs(e.size.width-e.size.height) < this.orangeEpsilon)
            && (a < this.maxOrangeArea));
    }

    // Find green button on AED; img must be blurred
    private Rect findGreenButton(Mat img, Rect rect) {
        double x1 = rect.x;
        double y1 = rect.y;
        double w = rect.width;
        double h = rect.height;
        double x2 = x1 + w;
        double y2 = y1 + h;

        // Threshold and get contours
        Mat mask = new Mat(this.h, this.w, CvType.CV_8UC1);
        Mat output = new Mat(this.h, this.w, CvType.CV_8UC1);
        Core.inRange(img, greenLower, greenUpper, mask);
        Core.bitwise_and(img, img, output, mask);
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(mask, contours, hierarchy,
                Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_NONE);

        // Find biggest contour
        MatOfPoint c;
        MatOfPoint2f c2f;
        double a;
        double maxArea = 0;
        RotatedRect e;
        Rect maxEllipse = defaultRect;
        int num;
        for (int i = 0; i < contours.size(); i++) {
            c = contours.get(i);
            a = Imgproc.contourArea(c);
            c2f = new MatOfPoint2f(c.toArray());
            num = c2f.toList().size();
            if (num >= 5 && a > maxArea) {
                e = Imgproc.fitEllipse(c2f);
                if (axisCheck(e.center.x, e.center.y, e.size.width-e.size.height, x1, y1, x2, y2)
                        && dimenCheck(e.size.width, e.size.height, a)) {
                    maxEllipse = e.boundingRect();
                    maxArea = a;
                }
            }
        }
        return maxEllipse;
    }

    private boolean axisCheck(double x, double y, double diff, double rx1, double ry1, double rx2, double ry2) {
        return ((Math.abs(diff) < this.axisEpsilon)
            && (rx1 <= x) && (x <= rx2)
            && (ry1 <= y) && (y <= ry2));
    }

    private boolean dimenCheck(double major, double minor, double area) {
        double ra = major / 2;
        double rb = minor / 2;
        double boundingArea = Math.PI  * ra * rb;
        double aCirc = Math.PI * ra * ra;
        return ((Math.abs(area - aCirc) < this.greenEpsilon)
            && (area >= this.minGreenArea)
            && (Math.abs(boundingArea - area) < this.axisEpsilon));
    }

}
