package de.stetro.tango.arnavigation.ui;

import android.hardware.display.DisplayManager;
import android.os.AsyncTask;
import android.os.Bundle;
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
import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;

import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.surface.RajawaliSurfaceView;

import java.nio.FloatBuffer;
import java.util.ArrayList;
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

    // This changes the Camera Texture and Intrinsics
    protected static final int ACTIVE_CAMERA_INTRINSICS = TangoCameraIntrinsics.TANGO_CAMERA_COLOR;
    protected static final int INVALID_TEXTURE_ID = -1;
    private static final double ACCURACY = 0.1;
    private static final String TAG = MainActivity.class.getSimpleName();
    protected AtomicBoolean tangoIsConnected = new AtomicBoolean(false);
    private float floorLevel = Float.NaN;

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
    private TangoSupport.IntersectionPointPlaneModelPair floorPlane = null;
    private int mDisplayRotation;
    private double mPointCloudPreviousTimeStamp;
    private double mPointCloudTimeToNextUpdate = UPDATE_INTERVAL_MS;
    private static final double UPDATE_INTERVAL_MS = 100.0;
    private boolean newPoints = false;
    private List<Vector3> floorPoints = new ArrayList<Vector3>();


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
        tango = new Tango(this);
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
        try{
            tango.connect(config);
            ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<>();
            framePairs.add(SOS_T_DEVICE_FRAME_PAIR);
            framePairs.add(DEVICE_T_PREVIOUS_FRAME_PAIR);
            tango.connectListener(framePairs, new mTangoUpdateListener());
            setupCameraProperties(tango);
            TangoSupport.initialize();
        } catch (TangoOutOfDateException e) {
            Log.e(TAG, getString(R.string.exception_out_of_date), e);
            Toast.makeText(this,R.string.exception_out_of_date,Toast.LENGTH_SHORT).show();
        } catch (TangoErrorException e) {
            Log.e(TAG, getString(R.string.exception_tango_error), e);
            Toast.makeText(this,R.string.exception_tango_error,Toast.LENGTH_SHORT).show();
        } catch (TangoInvalidException e) {
            Log.e(TAG, getString(R.string.exception_tango_invalid), e);
            Toast.makeText(this,R.string.exception_tango_invalid,Toast.LENGTH_SHORT).show();
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
                    if(newPoints){
                        synchronized (floorPoints){
                            List<Vector3> points = new ArrayList<Vector3>();
                            for(Vector3 v : floorPoints){
                                points.add(v.clone());
                            }
                            renderer.addToFloorPlan(points);
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
                float[] planePoint = findNeighbor(u, v, rgbFrameTimestamp);
                floorLevel = planePoint[1];
                Snackbar.make(view,R.string.floorSet,Snackbar.LENGTH_SHORT).show();
                Log.d(TAG,"Floor level: " + floorLevel);
                renderer.setFloorLevel(floorLevel);
                renderer.renderVirtualObjects(true);

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

    private TangoSupport.IntersectionPointPlaneModelPair doFitPlane(float u, float v, double rgbTimestamp) {
        TangoPointCloudData pointCloud = mPointCloudManager.getLatestPointCloud();

        if (pointCloud == null) {
            return null;
        }

                // We need to calculate the transform between the color camera at the
        // time the user clicked and the depth camera at the time the depth
        // cloud was acquired.
        TangoPoseData depthTcolorPose = TangoSupport.calculateRelativePose(
                pointCloud.timestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                rgbTimestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR);
        double[] identityTranslation = {0.0, 0.0, 0.0};
        double[] identityRotation = {0.0, 0.0, 0.0, 1.0};
        TangoSupport.IntersectionPointPlaneModelPair intersectionPointPlaneModelPair =
                TangoSupport.fitPlaneModelNearPoint(pointCloud,
                        identityTranslation, identityRotation, u, v, mDisplayRotation,
                        depthTcolorPose.translation, depthTcolorPose.rotation);

        double a = intersectionPointPlaneModelPair.planeModel[0];
        double b = intersectionPointPlaneModelPair.planeModel[1];
        double c = intersectionPointPlaneModelPair.planeModel[2];

        double length = Math.sqrt(a * a + b * b + c * c);

        intersectionPointPlaneModelPair.planeModel[0] = a / length;
        intersectionPointPlaneModelPair.planeModel[1] = b / length;
        intersectionPointPlaneModelPair.planeModel[2] = c / length;

        return intersectionPointPlaneModelPair;
    }

    private float[] findNeighbor(float u, float v, double rgbTimestamp){
        TangoPointCloudData pointCloud = mPointCloudManager.getLatestPointCloud();

        if (pointCloud == null) {
            return null;
        }
        TangoPoseData depthTcolorPose = TangoSupport.calculateRelativePose(
                pointCloud.timestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                rgbTimestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR);
        double[] identityTranslation = {0.0, 0.0, 0.0};
        double[] identityRotation = {0.0, 0.0, 0.0, 1.0};
        return TangoSupport.getDepthAtPointNearestNeighbor(pointCloud,identityTranslation,identityRotation,
                u,v,mDisplayRotation,depthTcolorPose.translation,depthTcolorPose.rotation);
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
            if(tangoUx != null){
                tangoUx.updatePointCloud(pointCloud);
            }
            mPointCloudManager.updatePointCloud(pointCloud);

            final double currentTimeStamp = pointCloud.timestamp;
                final double pointCloudFrameDelta =
                        (currentTimeStamp - mPointCloudPreviousTimeStamp) * 1000;
                mPointCloudPreviousTimeStamp = currentTimeStamp;
                final double averageDepth = getAveragedDepth(pointCloud.points,
                        pointCloud.numPoints);

                mPointCloudTimeToNextUpdate -= pointCloudFrameDelta;

            if(floorLevel != Float.NaN){
                new AsyncTask<TangoPointCloudData,Integer,Integer>(){
                    @Override
                    protected Integer doInBackground(TangoPointCloudData... params) {
                        TangoPointCloudData pointCloud = params[0];
                        if(floorLevel != Float.NaN && pointCloud.points != null){
                            float x,y,z,C,d;
                            while (pointCloud.points.hasRemaining()){
                                x = pointCloud.points.get();
                                y = pointCloud.points.get();
                                z = pointCloud.points.get();
//                                C = pointCloud.points.get();

                                d = Math.abs(y-floorLevel);
                                if (d < ACCURACY){
                                    synchronized (floorPoints){

                                        floorPoints.add(new Vector3(x,y,z));
                                        newPoints = true;
//                                    Log.d(TAG, String.format("Adding (%1$.3f,%2$.3f,%3$.3f) Distance: %4$.3f, Confidence: %5$.3f",x ,y ,z, d, C ));
                                    }
                                }
//                                else {
//                                    Log.d(TAG, String.format("Not adding (%1$.3f,%2$.3f,%3$.3f) Distance: %4$.3f, Confidence: %5$.3f",x ,y ,z, d, C ));
//                                }
                            }
                        }
                        return null;
                    }
                }.execute(pointCloud);
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
