import java.util.List;
import java.util.stream.Collectors;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.Timer;

public class CargoRunnable implements Runnable {

	static CargoPipeline pipeline = new CargoPipeline();

	Mat mat;
	Timer timer = new Timer();

	public CargoRunnable(Mat mat) {
		this.mat = mat;
	}

	@Override
	public void run() {
		pipeline.process(this.mat);
		List<Circle> circles = pipeline.filterContoursOutput().parallelStream().map((mat) -> {
			Circle circle = new Circle();
			float[] radius = new float[1];
			Point point = new Point();
			MatOfPoint2f mat2f = new MatOfPoint2f();
			mat.convertTo(mat2f, CvType.CV_32F);
			Imgproc.minEnclosingCircle(mat2f, point, radius);
			circle.center = point;
			circle.radius = radius[0];
			return circle;
		}).collect(Collectors.toList());
		circles.sort((c1, c2) -> { return Double.compare(c1.radius, c2.radius); });

		double[] cargoX = new double[circles.size()];
		double[] cargoY = new double[circles.size()];
		double[] cargoR = new double[circles.size()];

		//System.out.println("# of cargo: " + circles.size());

		for(int i = 0; i < circles.size(); i++) {
			cargoX[i] = circles.get(i).center.x;
			cargoY[i] = circles.get(i).center.y;
			cargoR[i] = circles.get(i).radius;
		}
		NetworkTableInstance.getDefault().getTable("vision/cargo").getEntry("x").setDoubleArray(cargoX);
		NetworkTableInstance.getDefault().getTable("vision/cargo").getEntry("y").setDoubleArray(cargoY);
		NetworkTableInstance.getDefault().getTable("vision/cargo").getEntry("r").setDoubleArray(cargoR);
	}

}