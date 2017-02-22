package de.stetro.tango.arnavigation.ui;

import android.hardware.display.DisplayManager;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.google.atap.tango.ux.TangoUx;
import com.google.atap.tango.ux.TangoUxLayout;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoException;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.experimental.TangoImageBuffer;
import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.surface.RajawaliSurfaceView;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.stetro.tango.arnavigation.R;
import de.stetro.tango.arnavigation.rendering.SceneRenderer;
import de.stetro.tango.arnavigation.ui.util.ScenePreFrameCallbackAdapter;
import de.stetro.tango.arnavigation.ui.views.MapView;


public class MainActivity extends AppCompatActivity implements View.OnTouchListener {

    // frame pairs for adf based ar pose tracking
    public static final TangoCoordinateFramePair SOS_T_DEVICE_FRAME_PAIR =
            new TangoCoordinateFramePair(
                    TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                    TangoPoseData.COORDINATE_FRAME_DEVICE);
    public static final TangoCoordinateFramePair DEVICE_T_PREVIOUS_FRAME_PAIR =
            new TangoCoordinateFramePair(
                    TangoPoseData.COORDINATE_FRAME_PREVIOUS_DEVICE_POSE,
                    TangoPoseData.COORDINATE_FRAME_DEVICE);

    public static final double UPDATE_INTERVAL_MS = 500.0;
    public static final int POINTCLOUD_SAMPLE_RATE = 5;
    public static final boolean MAP_CENTER = false;
    private static final double ACCURACY = 0.1;
    private static final double OBSTACLE_HEIGHT = 0.4;

    // This changes the Camera Texture and Intrinsics
    protected static final int ACTIVE_CAMERA_INTRINSICS = TangoCameraIntrinsics.TANGO_CAMERA_COLOR;
    protected static final int INVALID_TEXTURE_ID = -1;
    private static final String TAG = MainActivity.class.getSimpleName();
    protected AtomicBoolean tangoIsConnected = new AtomicBoolean(false);

    private double floorLevel = -1000.0f;
    protected AtomicBoolean tangoFrameIsAvailable = new AtomicBoolean(false);
    protected Tango tango;
    protected TangoUx tangoUx;
    protected TangoCameraIntrinsics intrinsics;

    protected DeviceExtrinsics extrinsics;
    private TangoPointCloudManager mPointCloudManager;
    protected int connectedTextureId;

    protected double rgbFrameTimestamp;

    protected double cameraPoseTimestamp;
    protected SceneRenderer renderer;
    @Bind(R.id.gl_main_surface_view)
    RajawaliSurfaceView mainSurfaceView;
    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.tango_ux_layout)
    TangoUxLayout uxLayout;
    @Bind(R.id.map_view)
    MapView mapView;
    @Bind(R.id.floatingActionButton)
    FloatingActionButton floatingActionButton;
    private TangoSupport.IntersectionPointPlaneModelPair floorPlane = null;
    private int mDisplayRotation;
    private double mPointCloudPreviousTimeStamp;
    private double mPointCloudTimeToNextUpdate = UPDATE_INTERVAL_MS;
    private boolean newPoints = false;
    private List<Vector3> floorPoints = new LinkedList<>();
    private List<Vector3> obstaclePoints = new LinkedList<>();
    private TangoImageBuffer mCurrentImageBuffer;
    private boolean capturePointcloud = false;
    private boolean newPointcloud = false;
    private DescriptiveStatistics calculationTimes = new DescriptiveStatistics();


    private static DeviceExtrinsics setupExtrinsics(Tango tango) {
        // Create camera to IMU transform.
        TangoCoordinateFramePair framePair = new TangoCoordinateFramePair();
        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR;
        TangoPoseData imuToRgbPose = tango.getPoseAtTime(0.0, framePair);

        // Create device to IMU transform.
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;
        TangoPoseData imuToDevicePose = tango.getPoseAtTime(0.0, framePair);

        // Create depth camera to IMU transform.
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH;
        TangoPoseData imuToDepthPose = tango.getPoseAtTime(0.0, framePair);

        return new DeviceExtrinsics(imuToDevicePose, imuToRgbPose, imuToDepthPose);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tango = new Tango(this,null);
        tangoUx = new TangoUx(this);
        renderer = new SceneRenderer(this);

        setContentView(R.layout.main_layout);
        ButterKnife.bind(this);
        setSupportActionBar(toolbar);
        tangoUx.setLayout(uxLayout);
        renderer.renderVirtualObjects(true);
        mainSurfaceView.setSurfaceRenderer(renderer);
        mainSurfaceView.setZOrderOnTop(false);
        mapView.setFloorPlanData(renderer.getFloorPlanData());
        mainSurfaceView.setOnTouchListener(this);
        mPointCloudManager = new TangoPointCloudManager();
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                capturePointcloud = true;
            }
        });

        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    synchronized (this) {
                        setDisplayRotation();
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }
            }, null);
        }

        calculationTimes.setWindowSize(100);
    }

    @Override
    protected void onResume() {
        super.onResume();
        synchronized (this) {
            if (tangoIsConnected.compareAndSet(false, true)) {
                try {
                    connectTango();
                    connectRenderer();
                } catch (TangoOutOfDateException e) {
                    message(R.string.exception_out_of_date);
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        synchronized (this) {
            if (tangoIsConnected.compareAndSet(true, false)) {
                renderer.getCurrentScene().clearFrameCallbacks();
                tango.disconnectCamera(ACTIVE_CAMERA_INTRINSICS);
                connectedTextureId = INVALID_TEXTURE_ID;
                tango.disconnect();
                tangoUx.stop();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.set_start_point:
                renderer.setStartPoint(getCurrentPose(), extrinsics);
                break;
            case R.id.set_end_point:
                renderer.setEndPoint(getCurrentPose(), extrinsics
                );
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void message(final int message_resource) {
        Toast.makeText(this, message_resource, Toast.LENGTH_SHORT).show();
    }

    protected void setupCameraProperties(Tango tango) {
        extrinsics = setupExtrinsics(tango);
        intrinsics = tango.getCameraIntrinsics(ACTIVE_CAMERA_INTRINSICS);
    }


    protected void connectTango() {
        TangoUx.StartParams params = new TangoUx.StartParams();
        tangoUx.start(params);
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DRIFT_CORRECTION, true);
        TangoSupport.initialize();
        try {
            tango.connect(config);
            ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<>();
            framePairs.add(SOS_T_DEVICE_FRAME_PAIR);
            framePairs.add(DEVICE_T_PREVIOUS_FRAME_PAIR);
            tango.connectListener(framePairs, new mTangoUpdateListener());

            tango.experimentalConnectOnFrameListener(TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                    new Tango.OnFrameAvailableListener() {
                        @Override
                        public void onFrameAvailable(TangoImageBuffer tangoImageBuffer, int i) {
                            mCurrentImageBuffer = copyImageBuffer(tangoImageBuffer);
                        }

                        TangoImageBuffer copyImageBuffer(TangoImageBuffer imageBuffer) {
                            ByteBuffer clone = ByteBuffer.allocateDirect(imageBuffer.data.capacity());
                            imageBuffer.data.rewind();
                            clone.put(imageBuffer.data);
                            imageBuffer.data.rewind();
                            clone.flip();
                            return new TangoImageBuffer(imageBuffer.width, imageBuffer.height,
                                    imageBuffer.stride, imageBuffer.frameNumber,
                                    imageBuffer.timestamp, imageBuffer.format, clone);
                        }
                    });

            setupCameraProperties(tango);
        } catch (TangoOutOfDateException e) {
            Log.e(TAG, getString(R.string.exception_out_of_date), e);
            Toast.makeText(this, R.string.exception_out_of_date, Toast.LENGTH_SHORT).show();
        } catch (TangoErrorException e) {
            Log.e(TAG, getString(R.string.exception_tango_error), e);
            Toast.makeText(this, R.string.exception_tango_error, Toast.LENGTH_SHORT).show();
        } catch (TangoInvalidException e) {
            Log.e(TAG, getString(R.string.exception_tango_invalid), e);
            Toast.makeText(this, R.string.exception_tango_invalid, Toast.LENGTH_SHORT).show();
        }
    }


    public TangoPoseData getCurrentPose() {
        return tango.getPoseAtTime(rgbFrameTimestamp, SOS_T_DEVICE_FRAME_PAIR);
    }

    protected void connectRenderer() {
        renderer.getCurrentScene().registerFrameCallback(new ScenePreFrameCallbackAdapter() {
            @Override
            public void onPreFrame(long sceneTime, double deltaTime) {
                synchronized (MainActivity.this) {
                    if (!tangoIsConnected.get()) {
                        return;
                    }
                    if (!renderer.isSceneCameraConfigured()) {
                        renderer.setProjectionMatrix(intrinsics);
                    }
                    if (connectedTextureId != renderer.getTextureId()) {
                        tango.connectTextureId(ACTIVE_CAMERA_INTRINSICS, renderer.getTextureId());
                        connectedTextureId = renderer.getTextureId();
                    }
                    if (tangoFrameIsAvailable.compareAndSet(true, false)) {
                        rgbFrameTimestamp = tango.updateTexture(ACTIVE_CAMERA_INTRINSICS);
                    }
                    if (rgbFrameTimestamp > cameraPoseTimestamp) {
                        TangoPoseData currentPose = getCurrentPose();
                        if (currentPose != null && currentPose.statusCode == TangoPoseData.POSE_VALID) {
                            renderer.updateRenderCameraPose(currentPose, extrinsics);
                            cameraPoseTimestamp = currentPose.timestamp;
                        }
                    }
                    if(newPointcloud){
                        // Update point cloud data.
                        TangoPointCloudData pointCloud = mPointCloudManager.getLatestPointCloud();
                        if (pointCloud != null) {
                            // Calculate the camera color pose at the camera frame update time in
                            // OpenGL engine.
                            TangoSupport.TangoMatrixTransformData transform =
                                    TangoSupport.getMatrixTransformAtTime(pointCloud.timestamp,
                                            TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                                            TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                                            TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                            TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                                            TangoSupport.ROTATION_IGNORED);
                            if (transform.statusCode == TangoPoseData.POSE_VALID) {
                                renderer.updatePointCloud(pointCloud, transform.matrix);
                            }
                        }
                        newPointcloud = false;
                    }
                    if (newPoints) {
                        synchronized (floorPoints) {
//                            List<Vector3> points = new ArrayList<Vector3>();
//                            for (Vector3 v : floorPoints) {
//                                points.add(v.clone());
//                            }
//                            renderer.addToFloorPlan(points);
                            List<List<Vector3>> points = new ArrayList<List<Vector3>>();
                            points.add(floorPoints);
                            points.add(obstaclePoints);
                            renderer.addToFloorPlan(points);
                            if(MAP_CENTER){
//                                renderer.setTrackPosition(points.get(0));
                                renderer.setTrackPosition(floorPoints.get(0));
                            }
                            floorPoints.clear();
                            newPoints = false;
                        }
                    }
                }
            }
        });
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
            // Calculate click location in u,v (0;1) coordinates.
            float u = motionEvent.getX() / view.getWidth();
            float v = motionEvent.getY() / view.getHeight();

            try {
                // Fit a plane on the clicked point using the latest poiont cloud data
//                TangoSupport.IntersectionPointPlaneModelPair planeModel = doFitPlane(u, v, rgbFrameTimestamp);
//                if (planeModel != null) {
//                    floorPlane = planeModel;
//                    Snackbar.make(view,R.string.floorSet,Snackbar.LENGTH_SHORT).show();
//                } else {
//                    Snackbar.make(view,R.string.no_depth_data, Snackbar.LENGTH_SHORT).show();
//                }
                float[] planeFitTransform;
                synchronized (this) {
                    float[] touchPosition = getDepthAtTouchPosition(u, v, mPointCloudManager.getLatestPointCloud());
                    if(touchPosition != null){

                        floorLevel = touchPosition[1];
                        Snackbar.make(view, R.string.floorSet, Snackbar.LENGTH_SHORT).show();
                        Log.d(TAG, "Floor level: " + floorLevel);
                        renderer.setFloorLevel(floorLevel);
                        renderer.renderVirtualObjects(true);
                    }
                }

            } catch (TangoException t) {
                Toast.makeText(getApplicationContext(),
                        R.string.failed_measurement,
                        Toast.LENGTH_SHORT).show();
                Log.e(TAG, getString(R.string.failed_measurement), t);
            } catch (SecurityException t) {
                Toast.makeText(getApplicationContext(),
                        R.string.failed_permissions,
                        Toast.LENGTH_SHORT).show();
                Log.e(TAG, getString(R.string.failed_permissions), t);
            }
        }
        return true;
    }

    /**
     * Use the TangoSupport library with point cloud data to calculate the depth
     * of the point closest to where the user touches the screen. It returns a
     * Vector3 in openGL world space.
     */
    private float[] getDepthAtTouchPosition(float u, float v, TangoPointCloudData pointCloud) {
//        TangoPointCloudData pointCloud = mPointCloudManager.getLatestPointCloud();
        if (pointCloud == null) {
            return null;
        }

        double rgbTimestamp;
        TangoImageBuffer imageBuffer = mCurrentImageBuffer;
        rgbTimestamp = imageBuffer.timestamp; // CPU.

        // We need to calculate the transform between the color camera at the
        // time the user clicked and the depth camera at the time the depth
        // cloud was acquired.
        TangoPoseData colorTdepthPose = TangoSupport.calculateRelativePose(
                rgbTimestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                pointCloud.timestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH);

        float[] point;
        double[] identityTranslation = {0.0, 0.0, 0.0};
        double[] identityRotation = {0.0, 0.0, 0.0, 1.0};
        point = TangoSupport.getDepthAtPointNearestNeighbor(pointCloud,
                colorTdepthPose.translation, colorTdepthPose.rotation,
                u, v, mDisplayRotation, identityTranslation, identityRotation);
        if (point == null) {
            return null;
        }

        // Get the transform from depth camera to OpenGL world at the timestamp of the cloud.
        float[] openGlPoint = colorToADFFrame(pointCloud, point);
        if (openGlPoint != null) return openGlPoint;
        return null;
    }

    private float[] colorToADFFrame(TangoPointCloudData pointCloud, float[] point) {
        TangoSupport.TangoMatrixTransformData transform =
            TangoSupport.getMatrixTransformAtTime(pointCloud.timestamp,
                    TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                    TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                    TangoSupport.ROTATION_IGNORED);
        return frameTransform(pointCloud.timestamp,point,transform);
    }

    private float[] depthToADFFrame(TangoPointCloudData pointCloud, float[] point) {
        TangoSupport.TangoMatrixTransformData transform =
                TangoSupport.getMatrixTransformAtTime(pointCloud.timestamp,
                        TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                        TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                        TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                        TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                        TangoSupport.ROTATION_IGNORED);
        return frameTransform(pointCloud.timestamp,point,transform);
    }

    private float[] frameTransform(double timestamp, float[] point,TangoSupport.TangoMatrixTransformData transform) {
        if (transform.statusCode == TangoPoseData.POSE_VALID) {
            float[] depthPoint = new float[]{point[0], point[1], point[2], 1};
            float[] openGlPoint = new float[4];
            Matrix.multiplyMV(openGlPoint, 0, transform.matrix, 0, depthPoint, 0);
            return openGlPoint;
        } else {
            Log.w(TAG, "Could not get depth camera transform at time " + timestamp);
        }
        return null;
    }

    private void setDisplayRotation() {
        Display display = getWindowManager().getDefaultDisplay();
        mDisplayRotation = display.getRotation();
    }

    private class mTangoUpdateListener extends Tango.TangoUpdateCallback {
        @Override
        public void onFrameAvailable(int cameraId) {
            if (cameraId == ACTIVE_CAMERA_INTRINSICS) {
                tangoFrameIsAvailable.set(true);
                mainSurfaceView.requestRender();
            }
        }

        @Override
        public void onTangoEvent(TangoEvent event) {
            if (tangoUx != null) {
                tangoUx.updateTangoEvent(event);
            }
        }

        @Override
        public void onPoseAvailable(TangoPoseData pose) {
            if (tangoUx != null) {
                tangoUx.updatePoseStatus(pose.statusCode);
            }
        }

        @Override
        public void onPointCloudAvailable(final TangoPointCloudData pointCloud) {
            if (tangoUx != null) {
                tangoUx.updatePointCloud(pointCloud);
            }
            mPointCloudManager.updatePointCloud(pointCloud);
            newPointcloud = true;

            if (floorLevel != -1000.0f) {

                final double currentTimeStamp = pointCloud.timestamp;
                final double pointCloudFrameDelta =
                        (currentTimeStamp - mPointCloudPreviousTimeStamp) * 1000;
                mPointCloudPreviousTimeStamp = currentTimeStamp;
                final double averageDepth = getAveragedDepth(pointCloud.points,
                        pointCloud.numPoints);

                mPointCloudTimeToNextUpdate -= pointCloudFrameDelta;


                if (mPointCloudTimeToNextUpdate < 0.0 || capturePointcloud ) {
                    if(calculationTimes.getN()>20){
                        mPointCloudTimeToNextUpdate = Math.max(UPDATE_INTERVAL_MS,calculationTimes.getMean());
                    } else {
                        mPointCloudTimeToNextUpdate = UPDATE_INTERVAL_MS;
                    }

                    if(pointCloud.points != null){
                        AsyncTask<TangoPointCloudData, Integer, List<List<float[]>> > pointCloudTask = new AsyncTask<TangoPointCloudData, Integer, List<List<float[]>> >() {

                            public long start;

                            @Override
                            protected List<List<float[]>> doInBackground(TangoPointCloudData... params) {
                                start = System.currentTimeMillis();
                                if (MAP_CENTER) {

                                    float[] openGLFrame = getDepthAtTouchPosition(0.5f, 0.5f, pointCloud);
                                    if (openGLFrame != null) {
                                        Log.d(TAG, "Pointcloud: " + openGLFrame[0] + " " + openGLFrame[1] + " " + openGLFrame[2]);

                                        double d = Math.abs(floorLevel - openGLFrame[1]);
                                        List<List<float[]>> result = new ArrayList<>();
                                        List<float[]> tmpResult = new ArrayList<>(1);
                                        if (d < ACCURACY) {
                                            tmpResult.add(openGLFrame);
                                            result.add(tmpResult);
                                            return result;
                                        } else {
                                            tmpResult.add(1,openGLFrame);
                                            return result;
                                            // TODO: add as obstacle
                                        }
                                    } else {
//                                    Log.d(TAG,"Point is null");
                                        return null;
                                    }
                                } else {
                                    TangoSupport.TangoMatrixTransformData transform =
                                            TangoSupport.getMatrixTransformAtTime(currentTimeStamp,
                                                    TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                                                    TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                                    TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                                                    TangoSupport.ROTATION_IGNORED);
                                    FloatBuffer points = pointCloud.points;
                                    float[] depthFrame;
                                    List<List<float[]>> result = new ArrayList<>();
                                    List<float[]> floor = new LinkedList<>();
                                    List<float[]> obstacles = new LinkedList<>();
                                    int i = POINTCLOUD_SAMPLE_RATE;
                                    while (points.hasRemaining()) {
                                        depthFrame = new float[]{points.get(), points.get(), points.get()};
                                        float C = points.get();
                                        if (i == 0) {
//                                            float[] worldFrame = depthToADFFrame(pointCloud, depthFrame);
//                                            float[] worldFrame = TangoSupport.transformPoint(transform.matrix,depthFrame);
                                            float[] worldFrame = frameTransform(currentTimeStamp,depthFrame,transform);
                                            if (Math.abs(floorLevel - worldFrame[1]) < ACCURACY) {
                                                floor.add(worldFrame);
                                            } else if(Math.abs(floorLevel - worldFrame[1]) > ACCURACY * 3) {
                                                obstacles.add(worldFrame);
                                            }
                                            i = POINTCLOUD_SAMPLE_RATE;
                                        } else {
                                            i--;
                                        }
                                    }
                                    Log.d(TAG, "found floorpoints: " + result.size());
                                    result.add(0,floor);
                                    result.add(1,obstacles);
                                    return result;
                                }
                            }

                            @Override
                            protected void onPostExecute(List<List<float[]>> floats) {
                                super.onPostExecute(floats);
                                if (floats != null) {
                                    synchronized (floorPoints) {
//                                        floorPoints.add(new Vector3(x,z,-y));
                                        for (float[] f : floats.get(0)) {
                                            floorPoints.add(new Vector3(f[0], f[1], f[2]));
                                        }
                                        for  (float[] f : floats.get(1)){
                                            obstaclePoints.add(new Vector3(f[0], f[1], f[2]));
                                        }
//                                        renderer.addToFloorPlan(floorPoints);
                                        newPoints = true;
                                    }
                                }
                                capturePointcloud = false;
                                long calcTime = System.currentTimeMillis() - start;
                                calculationTimes.addValue(calcTime);
                                Log.d(TAG, String.format("Mean Pointcloud calculations time: %1$.1f last: %2$d",  calculationTimes.getMean(),calcTime));
//                                Log.d(TAG, String.format("New average floor level %1$.3f, Stats: %2$s",floorLevel.getMean(),floorLevel.toString()));
                            }
                        };
                        pointCloudTask.execute(pointCloud);
                    } else {
                        capturePointcloud = false;
                    }
                }


            }

        }
    }

    private float getAveragedDepth(FloatBuffer pointCloudBuffer, int numPoints) {
        float totalZ = 0;
        float averageZ = 0;
        if (numPoints != 0) {
            int numFloats = 4 * numPoints;
            for (int i = 2; i < numFloats; i = i + 4) {
                totalZ = totalZ + pointCloudBuffer.get(i);
            }
            averageZ = totalZ / numPoints;
        }
        return averageZ;
    }
}
