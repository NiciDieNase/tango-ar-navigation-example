package de.stetro.tango.arnavigation.data;

import android.os.AsyncTask;
import android.util.Log;

import com.google.atap.tangoservice.TangoPointCloudData;
import com.projecttango.tangosupport.TangoSupport;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.rajawali3d.math.vector.Vector2;
import org.rajawali3d.math.vector.Vector3;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import de.stetro.tango.arnavigation.ui.util.MappingUtils;

/**
 * Created by felix on 13/03/17.
 */

public class EnvironmentMapper {

	public static final int QUAD_TREE_START = 0;
	public static final int QUAD_TREE_RANGE = 120;
	public static final int QUAD_TREE_DEPTH = 9;
	public static final int POINTCLOUD_SAMPLE_RATE = 9;
	private static final double ACCURACY = 0.15;
	private static final double OBSTACLE_HEIGHT = 0.4;
	private static final String TAG = EnvironmentMapper.class.getSimpleName();
	public static final double OBSTACLE_BUFFER = .1; // should be similar to size of smallest quad-tree-unit

	QuadTree map;
	private boolean running = false;
	private double floorLevel = -1000.0f;
	private DescriptiveStatistics calculationTimes = new DescriptiveStatistics();
	private OnMapUpdateListener listener;

    public interface OnMapUpdateListener{
		void onMapUpdate(QuadTree data);
		void onNewCalcTimes(double avg, long last);
	}

	public EnvironmentMapper(){
		map = new QuadTree(new Vector2(QUAD_TREE_START, QUAD_TREE_START), QUAD_TREE_RANGE, QUAD_TREE_DEPTH);
	}

	public EnvironmentMapper(QuadTree map) {
		this.map = map;
	}

	public void mapPointCloud(final TangoPointCloudData pointCloud) {
		if (this.getFloorLevel() != -1000.0f) {

			final double currentTimeStamp = pointCloud.timestamp;
				if (pointCloud.points != null) {
					new AsyncTask<TangoPointCloudData, Integer,QuadTree>() {

						public long start;

						@Override
						protected void onPreExecute() {
							running = true;
						}

						@Override
						protected QuadTree doInBackground(TangoPointCloudData... params) {
							start = System.currentTimeMillis();
							TangoSupport.TangoMatrixTransformData transform = MappingUtils.getTransform(currentTimeStamp);
							FloatBuffer points = pointCloud.points;
							float[] depthFrame;
							List<List<float[]>> result = new ArrayList<>();
							List<Vector3> floor = new LinkedList<>();
							List<Vector3> obstacles = new LinkedList<>();
							int i = POINTCLOUD_SAMPLE_RATE;
							while (points.hasRemaining()) {
								depthFrame = new float[]{points.get(), points.get(), points.get()};
								float C = points.get();
								if (i == 0) {
									float[] worldFrame = MappingUtils.frameTransform(currentTimeStamp, depthFrame, transform);
									double d = Math.abs(getFloorLevel() - worldFrame[1]);
									if (d < ACCURACY) {
										floor.add(new Vector3(worldFrame[0],worldFrame[1],worldFrame[2]));
									} else if (d > OBSTACLE_HEIGHT) {
										obstacles.add(new Vector3(worldFrame[0]+ OBSTACLE_BUFFER,worldFrame[1],worldFrame[2]));
										obstacles.add(new Vector3(worldFrame[0]- OBSTACLE_BUFFER,worldFrame[1],worldFrame[2]));
										obstacles.add(new Vector3(worldFrame[0], worldFrame[1],worldFrame[2]+ OBSTACLE_BUFFER));
										obstacles.add(new Vector3(worldFrame[0], worldFrame[1],worldFrame[2]- OBSTACLE_BUFFER));
										obstacles.add(new Vector3(worldFrame[0]+ OBSTACLE_BUFFER,worldFrame[1],worldFrame[2]+ OBSTACLE_BUFFER));
										obstacles.add(new Vector3(worldFrame[0]+ OBSTACLE_BUFFER,worldFrame[1],worldFrame[2]- OBSTACLE_BUFFER));
										obstacles.add(new Vector3(worldFrame[0]- OBSTACLE_BUFFER,worldFrame[1],worldFrame[2]- OBSTACLE_BUFFER));
										obstacles.add(new Vector3(worldFrame[0]- OBSTACLE_BUFFER,worldFrame[1],worldFrame[2]- OBSTACLE_BUFFER));
									}
									i = POINTCLOUD_SAMPLE_RATE;
								} else {
									i--;
								}
							}
							Log.d(TAG, "found floorpoints: " + result.size());

							map.setObstacle(obstacles);
							map.setFilledInvalidate3(floor);
							map.clearObstacleCount();
							return map.clone();
						}

						@Override
						protected void onPostExecute(QuadTree data) {
							if(listener != null){
								listener.onMapUpdate(data);
							}
							running = false;
							long calcTime = System.currentTimeMillis() - start;
							calculationTimes.addValue(calcTime);
							Log.d(TAG, String.format("Mean Pointcloud calculations time: %1$.1f last: %2$d", calculationTimes.getMean(), calcTime));
							if(listener != null){
								listener.onNewCalcTimes(calculationTimes.getMean(),calcTime);
							}
						}
					}.execute(pointCloud);
				}
			}
		}

	public boolean isRunning() {
		return running;
	}

	public double getFloorLevel() {
		return floorLevel;
	}

	public void setFloorLevel(double floorLevel) {
		this.floorLevel = floorLevel;
	}

	public void setListener(OnMapUpdateListener listener) {
		this.listener = listener;
	}

	public void toggle(Vector2 v2){
		QuadTree node = map.getNodeAt(v2);
		map.forceFilledInvalidate(v2,!node.isFilled());
		if(listener != null){
			listener.onMapUpdate(map.clone());
		}
	}
}
