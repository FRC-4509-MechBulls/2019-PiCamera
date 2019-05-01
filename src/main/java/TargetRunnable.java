import java.util.ArrayList;
import java.util.List;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.imgproc.Imgproc;

import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.Timer;

public class TargetRunnable implements Runnable {

	static NetworkTable targetsTable = NetworkTableInstance.getDefault().getTable("vision/targets");

	static TargetPipeline pipeline = new TargetPipeline();
	static RotatedRect leftRect, rightRect;

	Mat mat;
	Timer timer = new Timer();

	public TargetRunnable(Mat mat) {
		this.mat = mat;
	}

	@Override
	public void run() {
		pipeline.process(this.mat);
		RotatedRect[] rotatedRects = findTargets(pipeline.filterContoursOutput());
		//System.out.println("# of targets: " + rotatedRects.length);
		if(rotatedRects.length == 2 && rotatedRects[0] != rotatedRects[1] && diff(rotatedRects[0].angle, -75.5) < 10 && diff(rotatedRects[1].angle, -14.5) < 10) {
			putTargets(rotatedRects[0], rotatedRects[1]);
		} else if(rotatedRects.length == 1 && diff(rotatedRects[0].angle, -75.5) < 10) {
			putTargets(rotatedRects[0], new RotatedRect());
		} else if(rotatedRects.length == 1 && diff(rotatedRects[0].angle, -14.5) < 10) {
			putTargets(new RotatedRect(), rotatedRects[0]);
		} else {
			resetTargetEntries();
		}
	}

	public static double distance(double width, double height) {
		return (distanceW(width) + distanceH(height)) / 2;
	}

	public static double distanceW(double width) {
		double focalLength = 393.903;
		double realWidth = 3.313;
		return (focalLength * realWidth) / width;
	}

	public static double distanceH(double height) {
		double focalLength = 370.815;
		double realHeight = 5.825;
		return (focalLength * realHeight) / height;
	}

	public static double diff(double a, double b) {
		return Math.abs(Math.abs(a) - Math.abs(b));
	}

	public static double distanceToCenter(Point a) {
		return Math.sqrt(Math.pow(a.x - 208, 2) + Math.pow(a.y - 120, 2));
	}

	public static void resetTargetEntries() {
		targetsTable.getEntry("contour_left").setDoubleArray(new double[6]);
		targetsTable.getEntry("contour_right").setDoubleArray(new double[6]);
	}

	public static RotatedRect[] findTargets(List<MatOfPoint> contours) {
		if(contours.size() == 0) return new RotatedRect[0];
		List<RotatedRect> rotatedBoxes = new ArrayList<RotatedRect>();
		for(MatOfPoint mat : contours) {
			MatOfPoint2f mat2f = new MatOfPoint2f();
			mat.convertTo(mat2f, CvType.CV_32F);
			rotatedBoxes.add(Imgproc.minAreaRect(mat2f));
		}

		rotatedBoxes.removeIf((r) -> { return !(Math.abs(r.angle - -75.5) < 10 || Math.abs(r.angle - -14.5) < 10); });
		rotatedBoxes.sort((a, b) -> { return Double.compare(distanceToCenter(a.center), distanceToCenter(b.center)); });

		//System.out.println("# of rects: " + rotatedBoxes.size());

		int iLeft = 0, iRight = 0;
		for(int i = 0; i < rotatedBoxes.size(); i++) {
			if(diff(rotatedBoxes.get(i).angle, -75.5) < diff(rotatedBoxes.get(iLeft).angle, -75.5)) {
				iLeft = i;
			} else if(diff(rotatedBoxes.get(i).angle, -14.5) < diff(rotatedBoxes.get(iRight).angle, -14.5)) {
				iRight = i;
			}
		}

		if(iLeft == iRight) {
			return new RotatedRect[]{ rotatedBoxes.get(iLeft) };
		} else {
			return new RotatedRect[]{ rotatedBoxes.get(iLeft), rotatedBoxes.get(iRight) };
		}
	}

	public static void putTargets(RotatedRect lTarget, RotatedRect rTarget) {
		Rect leftBox = lTarget.boundingRect();
		targetsTable.getEntry("contour_left").setDoubleArray(new double[]{
			lTarget.center.x, lTarget.center.y, lTarget.size.width, lTarget.size.height, lTarget.angle, distance(leftBox.width, leftBox.height)
		});
		Rect rightBox = rTarget.boundingRect();
		targetsTable.getEntry("contour_right").setDoubleArray(new double[]{
			rTarget.center.x, rTarget.center.y, rTarget.size.width, rTarget.size.height, rTarget.angle, distance(rightBox.width, rightBox.height)
		});
	}

}