/*----------------------------------------------------------------------------*/
/* Copyright (c) 2018 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import edu.wpi.cscore.MjpegServer;
import edu.wpi.cscore.UsbCamera;
import edu.wpi.cscore.VideoSource;
import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.networktables.EntryListenerFlags;
import edu.wpi.first.networktables.NetworkTableInstance;

import org.opencv.core.Mat;

public final class Main {
	private static String configFile = "/boot/frc.json";

	@SuppressWarnings("MemberName")
	public static class CameraConfig {
		public String name;
		public String path;
		public JsonObject config;
		public JsonElement streamConfig;
	}

	@SuppressWarnings("MemberName")
	public static class SwitchedCameraConfig {
		public String name;
		public String key;
	};

	public static int team;
	public static boolean server;
	public static List<CameraConfig> cameraConfigs = new ArrayList<>();
	public static List<SwitchedCameraConfig> switchedCameraConfigs = new ArrayList<>();
	public static List<VideoSource> cameras = new ArrayList<>();

	public static int source;
	public static final int hatchCamera = 1, cargoCamera = 0;

	public static boolean running = false;

	private Main() {
	}

	/**
	 * Report parse error.
	 */
	public static void parseError(String str) {
		System.err.println("config error in '" + configFile + "': " + str);
	}

	/**
	 * Read single camera configuration.
	 */
	public static boolean readCameraConfig(JsonObject config) {
		CameraConfig cam = new CameraConfig();

		// name
		JsonElement nameElement = config.get("name");
		if(nameElement == null) {
			parseError("could not read camera name");
			return false;
		}
		cam.name = nameElement.getAsString();

		// path
		JsonElement pathElement = config.get("path");
		if(pathElement == null) {
			parseError("camera '" + cam.name + "': could not read path");
			return false;
		}
		cam.path = pathElement.getAsString();

		// stream properties
		cam.streamConfig = config.get("stream");

		cam.config = config;

		cameraConfigs.add(cam);
		return true;
	}

	/**
	 * Read single switched camera configuration.
	 */
	public static boolean readSwitchedCameraConfig(JsonObject config) {
		SwitchedCameraConfig cam = new SwitchedCameraConfig();

		// name
		JsonElement nameElement = config.get("name");
		if(nameElement == null) {
			parseError("could not read switched camera name");
			return false;
		}
		cam.name = nameElement.getAsString();

		// path
		JsonElement keyElement = config.get("key");
		if(keyElement == null) {
			parseError("switched camera '" + cam.name + "': could not read key");
			return false;
		}
		cam.key = keyElement.getAsString();

		switchedCameraConfigs.add(cam);
		return true;
	}

	/**
	 * Read configuration file.
	 */
	@SuppressWarnings("PMD.CyclomaticComplexity")
	public static boolean readConfig() {
		// parse file
		JsonElement top;
		try {
			top = new JsonParser().parse(Files.newBufferedReader(Paths.get(configFile)));
		} catch(IOException ex) {
			System.err.println("could not open '" + configFile + "': " + ex);
			return false;
		}

		// top level must be an object
		if(!top.isJsonObject()) {
			parseError("must be JSON object");
			return false;
		}
		JsonObject obj = top.getAsJsonObject();

		// team number
		JsonElement teamElement = obj.get("team");
		if(teamElement == null) {
			parseError("could not read team number");
			return false;
		}
		team = teamElement.getAsInt();

		// ntmode (optional)
		if(obj.has("ntmode")) {
			String str = obj.get("ntmode").getAsString();
			if("client".equalsIgnoreCase(str)) {
				server = false;
			} else if("server".equalsIgnoreCase(str)) {
				server = true;
			} else {
				parseError("could not understand ntmode value '" + str + "'");
			}
		}

		// cameras
		JsonElement camerasElement = obj.get("cameras");
		if(camerasElement == null) {
			parseError("could not read cameras");
			return false;
		}
		JsonArray cameras = camerasElement.getAsJsonArray();
		for(JsonElement camera : cameras) {
			if(!readCameraConfig(camera.getAsJsonObject())) {
				return false;
			}
		}

		if(obj.has("switched cameras")) {
			JsonArray switchedCameras = obj.get("switched cameras").getAsJsonArray();
			for(JsonElement camera : switchedCameras) {
				if(!readSwitchedCameraConfig(camera.getAsJsonObject())) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Start running the camera.
	 */
	public static VideoSource startCamera(CameraConfig config) {
		System.out.println("Starting camera '" + config.name + "' on " + config.path);
		CameraServer inst = CameraServer.getInstance();
		UsbCamera camera = new UsbCamera(config.name, config.path);
		MjpegServer server = inst.startAutomaticCapture(camera);

		Gson gson = new GsonBuilder().create();

		camera.setConfigJson(gson.toJson(config.config));
		camera.setConnectionStrategy(VideoSource.ConnectionStrategy.kKeepOpen);

		if(config.streamConfig != null) {
			server.setConfigJson(gson.toJson(config.streamConfig));
		}

		return camera;
	}

	/**
	 * Start running the switched camera.
	 */
	public static MjpegServer startSwitchedCamera(SwitchedCameraConfig config) {
		System.out.println("Starting switched camera '" + config.name + "' on " + config.key);
		MjpegServer server = CameraServer.getInstance().addSwitchedCamera(config.name);

		NetworkTableInstance.getDefault().getEntry(config.key).addListener(event -> {
			if(event.value.isDouble()) {
				int i = (int) event.value.getDouble();
				if(i >= 0 && i < cameras.size()) {
					server.setSource(cameras.get(i));
				}
			} else if(event.value.isString()) {
				String str = event.value.getString();
				for(int i = 0; i < cameraConfigs.size(); i++) {
					if(str.equals(cameraConfigs.get(i).name)) {
						server.setSource(cameras.get(i));
						break;
					}
				}
			}
		}, EntryListenerFlags.kImmediate | EntryListenerFlags.kNew | EntryListenerFlags.kUpdate);

		return server;
	}

	/**
	 * Main.
	 */
	public static void main(String... args) {
		if(args.length > 0) {
			configFile = args[0];
		}

		// read configuration
		if(!readConfig()) {
			return;
		}

		// start NetworkTables
		NetworkTableInstance ntinst = NetworkTableInstance.getDefault();
		if(server) {
			System.out.println("Setting up NetworkTables server");
			ntinst.startServer();
		} else {
			System.out.println("Setting up NetworkTables client for team " + team);
			ntinst.startClientTeam(team);
		}
		ntinst.setUpdateRate(0.02);

		// start cameras
		for(CameraConfig config : cameraConfigs) {
			cameras.add(startCamera(config));
		}

		// start switched cameras
		for(SwitchedCameraConfig config : switchedCameraConfigs) {
			startSwitchedCamera(config);
		}

		source = (int)NetworkTableInstance.getDefault().getTable("vision").getEntry("source").getDouble(0) % (cameras.size() | 1);
		ntinst.getTable("vision").addEntryListener("source", (table, key, entry, value, flags) -> {
			source = (int)value.getDouble() % (cameras.size() | 1);
		}, EntryListenerFlags.kImmediate | EntryListenerFlags.kNew | EntryListenerFlags.kUpdate);

		final Mat VISION_MAT = new Mat();

		Thread grabThread = new Thread(() -> {
			while(running) {
				if(cameras.size() >= 1) {
					if(source >= cameras.size()) source = cameras.size() - 1;
					CameraServer.getInstance().getVideo(cameras.get(source)).grabFrame(VISION_MAT);
				}
			}
		});

		Thread visionThread = new Thread(() -> {
			//CargoRunnable cargoRunnable = new CargoRunnable(VISION_MAT);
			TargetRunnable targetRunnable = new TargetRunnable(VISION_MAT);
			while(running) {
				if(!VISION_MAT.empty()) {
					//cargoRunnable.run();
					targetRunnable.run();
				}
			}
		});

		running = true;
		grabThread.start();
		visionThread.start();

		// loop forever
		for(;;) {
			try {
				Thread.sleep(10000);
			} catch(InterruptedException ex) {}
		}
	}
}
