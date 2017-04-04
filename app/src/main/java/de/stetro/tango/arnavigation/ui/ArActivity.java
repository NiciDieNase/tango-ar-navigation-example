package de.stetro.tango.arnavigation.ui;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

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
import com.projecttango.rajawali.ScenePoseCalculator;
import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.rajawali3d.math.vector.Vector2;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.surface.RajawaliSurfaceView;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import butterknife.BindView;
import butterknife.ButterKnife;
import de.stetro.tango.arnavigation.R;
import de.stetro.tango.arnavigation.data.EnvironmentMapper;
import de.stetro.tango.arnavigation.data.PoiAdapter;
import de.stetro.tango.arnavigation.data.QuadTree;
import de.stetro.tango.arnavigation.data.persistence.EnvironmentDAO;
import de.stetro.tango.arnavigation.data.persistence.PoiDAO;
import de.stetro.tango.arnavigation.rendering.SceneRenderer;
import de.stetro.tango.arnavigation.ui.SelectEnvironmentFragment.EnvironmentSelectionListener;
import de.stetro.tango.arnavigation.ui.util.MappingUtils;
import de.stetro.tango.arnavigation.ui.util.ScenePreFrameCallbackAdapter;
import de.stetro.tango.arnavigation.ui.views.MapView;

import static de.stetro.tango.arnavigation.ui.util.MappingUtils.getDepthAtTouchPosition;

public class ArActivity extends AppCompatActivity implements View.OnTouchListener,
		EnvironmentSelectionListener, SceneRenderer.OnRoutingErrorListener {

	public static final String MAP_TRANSFORM = "map_transform";
	private EnvironmentDAO environment;

	public enum ActivityState {mapping, editing, localizing, navigating, undefined;}

	// frame pairs for adf based ar pose tracking
	public static final TangoCoordinateFramePair SOS_T_DEVICE_FRAME_PAIR =
			new TangoCoordinateFramePair(
					TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
					TangoPoseData.COORDINATE_FRAME_DEVICE);

	public static final TangoCoordinateFramePair DEVICE_T_PREVIOUS_FRAME_PAIR =
			new TangoCoordinateFramePair(
					TangoPoseData.COORDINATE_FRAME_PREVIOUS_DEVICE_POSE,
					TangoPoseData.COORDINATE_FRAME_DEVICE);
	public static final TangoCoordinateFramePair ADF_T_DEVICE_FRAME_PAIR =
			new TangoCoordinateFramePair(
					TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
					TangoPoseData.COORDINATE_FRAME_DEVICE);

	public static final TangoCoordinateFramePair ADF_T_SOS_FRAME_PAIR =
			new TangoCoordinateFramePair(
					TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
					TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE);

	// This changes the Camera Texture and Intrinsics
	protected static final int ACTIVE_CAMERA_INTRINSICS = TangoCameraIntrinsics.TANGO_CAMERA_COLOR;
	protected static final int INVALID_TEXTURE_ID = -1;
	private static final String TAG = ArActivity.class.getSimpleName();
	public static final boolean LEARNINGMODE_ENABLED = true;
	public static final boolean ENABLED_DEFAULT = true;
	public static final String KEY_ENVIRONMENT_ID = "environment_id";
	protected AtomicBoolean tangoIsConnected = new AtomicBoolean(false);

	protected AtomicBoolean tangoFrameIsAvailable = new AtomicBoolean(false);
	protected Tango tango;
	private long motivationEndDelay = 0;
	private int motivationStartDelay = 2000;
	private long environment_id;

	private PoiAdapter poiAdapter;
	private PoiAdapter mAdapter;
	private boolean motivating;
	private Snackbar recognzingSnackbar;

	protected TangoUx tangoUx;
	protected TangoCameraIntrinsics intrinsics;
	protected SceneRenderer renderer;
	protected EnvironmentMapper mapper;
	protected DeviceExtrinsics extrinsics;
	private TangoPointCloudManager mPointCloudManager;

	protected int connectedTextureId;
	protected double rgbFrameTimestamp;

	protected double cameraPoseTimestamp;

	RajawaliSurfaceView mainSurfaceView;
	@BindView(R.id.toolbar) Toolbar toolbar;

	@BindView(R.id.tango_ux_layout) TangoUxLayout uxLayout;
	@BindView(R.id.map_view) MapView mapView;
	@BindView(R.id.fab_pause) FloatingActionButton fabPause;
	@BindView(R.id.fab_save) FloatingActionButton fabSave;
	@BindView(R.id.fab_addpoi) FloatingActionButton fabAddPoi;
	@BindView(R.id.progressSpinner) ProgressBar progressBar;
	@BindView(R.id.drawer_layout) DrawerLayout mDrawerLayout;
	@BindView(R.id.left_drawer) RecyclerView mRecyclerView;
	private ActionBarDrawerToggle mDrawerToggle;
	private int mDisplayRotation;

	private TangoImageBuffer mCurrentImageBuffer;
	private DescriptiveStatistics calculationTimes = new DescriptiveStatistics();
	private String adfuuid = "";
	QuadTree tree = null;
	private boolean capturePointcloud = true;

	private boolean newPointcloud = false;
	private boolean localized = false;
	private boolean togglePointcloud = false;
	private boolean toggleFloorplan = false;
	private boolean newQuadtree = false;
	private boolean updatePOIs = false;
	private QuadTree newMapData;
	private boolean enableLoadingSpinner;
	private LoadingDialogFragment dialog;

	private ActivityState currentState = ActivityState.undefined;

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

		setContentView(R.layout.main_layout);
		ButterKnife.bind(this);
		setSupportActionBar(toolbar);

		setupSurfaceView();

		if(savedInstanceState != null && savedInstanceState.containsKey(MAP_TRANSFORM)){
			this.mapView.setActiveTransformation(savedInstanceState.getDoubleArray(MAP_TRANSFORM));
		}

		Bundle extras = getIntent().getExtras();
		boolean enabledDefault = ENABLED_DEFAULT;
		boolean floorplanEnabled = enabledDefault;
		boolean motivationEnabled = enabledDefault;
		boolean pathEnabled = enabledDefault;
		boolean path2Enabled = enabledDefault;
		boolean coinsEnabled = enabledDefault;
		if (extras != null) {
			// get what to render from intent
			enabledDefault = extras.getBoolean(ScenarioSelectActivity.KEY_ENABLED_DEFAULT, ENABLED_DEFAULT);
			floorplanEnabled = extras.getBoolean(ScenarioSelectActivity.KEY_FLOORPLAN_ENABLED, enabledDefault);
			motivationEnabled = extras.getBoolean(ScenarioSelectActivity.KEY_MOTIVATION_ENABELD, enabledDefault);
			pathEnabled = extras.getBoolean(ScenarioSelectActivity.KEY_PATH_ENABLED, enabledDefault);
			path2Enabled = extras.getBoolean(ScenarioSelectActivity.KEY_PATH2_ENABLED, enabledDefault);
			coinsEnabled = extras.getBoolean(ScenarioSelectActivity.KEY_COINS_ENABLED, enabledDefault);
			motivationEndDelay = extras.getLong(ScenarioSelectActivity.KEY_DELAY_SEC,0);
			enableLoadingSpinner = extras.getBoolean(ScenarioSelectActivity.KEY_LOADINGSPINNER_ENABLED, enabledDefault);
			Snackbar.make(uxLayout,"Delay = " + motivationEndDelay, Snackbar.LENGTH_SHORT).show();

			environment_id = extras.getLong(KEY_ENVIRONMENT_ID, 0);
			if (environment_id != 0) {
				environment = EnvironmentDAO.findById(EnvironmentDAO.class, environment_id);
//				if(environment.getMapTransform() != null){
//					mapView.setActiveTransformation(environment.getMapTransform());
//				}
				adfuuid = environment.getADFUUID();
				tree = loadFromFile(environment.getADFUUID());
				if (tree != null) {
					mapper = new EnvironmentMapper(tree);
					mapper.setFloorLevel(environment.getFloorLevel());
					renderer = new SceneRenderer(this, tree,floorplanEnabled,motivationEnabled,pathEnabled,path2Enabled,coinsEnabled);
					currentState = ActivityState.localizing;
				} else {
					Log.d(TAG,"Failed to load map");
				}
				capturePointcloud = false;
				fabPause.hide();
				fabSave.hide();
				updatePOIs = true;
//				setRecognizingSnackbar(true);
			}

		}
		if(mapper == null){
			mapper = new EnvironmentMapper();
			renderer = new SceneRenderer(this,floorplanEnabled,motivationEnabled,pathEnabled,path2Enabled,coinsEnabled);
			currentState = ActivityState.mapping;
			fabAddPoi.hide();
		}
		renderer.setListerner(this);
		mapper.setListener(new EnvironmentMapper.OnMapUpdateListener() {
			@Override
			public void onMapUpdate(QuadTree data) {
				newMapData = data;
				newQuadtree = true;
			}

			@Override
			public void onNewCalcTimes(double avg, long last) {

			}
		});


		tangoUx = new TangoUx(this);
		tangoUx.setLayout(uxLayout);
		mainSurfaceView.setSurfaceRenderer(renderer);
		mainSurfaceView.setZOrderOnTop(false);
		mapView.setFloorPlanData(renderer.getFloorPlanData());
		mainSurfaceView.setOnTouchListener(this);
		mPointCloudManager = new TangoPointCloudManager();

		setupFABs();

		startActivityForResult(Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE),
				Tango.TANGO_INTENT_ACTIVITYCODE);

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

		if(environment_id != 0){
			setupDrawer(environment_id);
		} else {
			mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
		}

		if(enableLoadingSpinner){
			showProgressBar("Please walk around to localize");
		}
	}

	private void setupSurfaceView() {
		mainSurfaceView = new RajawaliSurfaceView(this);
		View glSurfaceView = findViewById(R.id.gl_main_surface_view);
		ViewGroup parent = (ViewGroup) glSurfaceView.getParent();
		int index = parent.indexOfChild(glSurfaceView);
		parent.removeView(glSurfaceView);
		parent.addView(mainSurfaceView, index);
	}

	private void setupFABs() {
		fabPause.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				capturePointcloud = !capturePointcloud;
				if (capturePointcloud) {
					fabPause.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_media_pause));
					currentState = ActivityState.mapping;
					renderer.renderSphere(false);
				} else {
					fabPause.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_media_play));
					currentState = ActivityState.editing;
//					renderer.renderSphere(true);
				}
			}
		});

		fabSave.setOnClickListener(new OnSaveButtonClickListener());

		fabAddPoi.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				TangoPoseData poseData = getCurrentADFPose();
				final float[] p = poseData.getTranslationAsFloats();
				new SaveDialogFragment().setListener(new SaveDialogFragment.OnSaveListener() {
					@Override
					public void onSave(String title, String description) {
						addPOI(p, title, description);
						List<PoiDAO> poiDAOs = PoiDAO.find(PoiDAO.class,
								"environment_id = ?", String.valueOf(environment_id));
						poiAdapter.update(poiDAOs);
					}
				}).show(getFragmentManager(),"saveDialog");
			}
		});
	}

	private void setupDrawer(long environmentID) {
		List<PoiDAO> poiDAOs = PoiDAO.find(PoiDAO.class, "environment_id = ?", String.valueOf(environmentID));
		mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
		mRecyclerView.setHasFixedSize(true);
		poiAdapter = new PoiAdapter(poiDAOs, new PoiAdapter.OnPoiSelectedListener() {
			@Override
			public void onPoiSelected(PoiDAO poi) {
				double[] v = getCurrentADFPose().translation;
				Vector3 start = new Vector3(v[0], v[2], -v[1]);
				renderer.setPath(start, poi.getPosition());
				renderer.setPathHeight(v[2]-0.5);
				renderer.showPOI(poi.getPosition());
				mDrawerLayout.closeDrawer(Gravity.LEFT);
			}
		});
		mAdapter = poiAdapter;
		mRecyclerView.setAdapter(mAdapter);
		mDrawerToggle = new ActionBarDrawerToggle(
				this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close);
		mDrawerLayout.addDrawerListener(mDrawerToggle);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
	}


	@Override
	protected void onPostCreate(@Nullable Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		if(mDrawerToggle != null){
			mDrawerToggle.syncState();
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if(mDrawerToggle != null){
			mDrawerToggle.onConfigurationChanged(newConfig);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		synchronized (this) {
			if (tangoIsConnected.compareAndSet(false, true)) {
				try {
					if (checkAndRequestPermissions()) {
						connectTango();
						renderer.getCurrentScene()
								.registerFrameCallback(new MyPreFrameCallbackAdapter());
					}
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
			if (tangoIsConnected.compareAndSet(true, false) && tango != null) {
				renderer.getCurrentScene().clearFrameCallbacks();
				tango.disconnectCamera(ACTIVE_CAMERA_INTRINSICS);
				connectedTextureId = INVALID_TEXTURE_ID;
				tango.disconnect();
				tangoUx.stop();
			}
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		double[] activeTransformation = mapView.getActiveTransformation();
		outState.putDoubleArray(MAP_TRANSFORM,activeTransformation);
		super.onSaveInstanceState(outState);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if(environment_id == 0){
			getMenuInflater().inflate(R.menu.main_menu, menu);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item)){
			return true;
		}
		switch (item.getItemId()) {
			case R.id.set_start_point:
				renderer.setStartPoint(getCurrentADFPose(), extrinsics);
				break;
			case R.id.set_end_point:
				renderer.setEndPoint(getCurrentADFPose(), extrinsics
				);
				break;
			case R.id.toggle_point_cloud:
				togglePointcloud = true;
				break;
			case R.id.toggle_floor_plan:
				toggleFloorplan = true;
				break;
			case R.id.load_environment:
				new SelectEnvironmentFragment().setEnvironmentSelectionListener(this).show(getSupportFragmentManager(),"loadEnvDialog");
				break;
			case R.id.clear_pois:
				if(environment_id != 0){
					PoiDAO.deleteAll(PoiDAO.class, "environment_id = ?", String.valueOf(environment_id));
				} else {
					PoiDAO.deleteAll(PoiDAO.class);
				}
				updatePOIs = true;
				break;
			case R.id.action_end_motivation:
				new AsyncTask<Void, Integer, Object>() {
					@Override
					protected Object doInBackground(Void... params) {
						renderer.finishMotivation();
						return null;
					}
				}.execute();
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onRoutingError(int msg) {
		message(msg);
	}

	private void onInitialLocalization() {
		// TODO render objects we need to show
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				hideProgressBar();
				setRecognizingSnackbar(false);
				if(mAdapter != null && mAdapter.getItemCount() > 0){
					mDrawerLayout.openDrawer(Gravity.LEFT);
				}
			}
		});
	}

	private void addPOI(float[] p, String name, String description) {
		PoiDAO poi = new PoiDAO(environment_id, name, description, p[0], p[1], p[2]);
		poi.save();
		updatePOIs = true;
	}

	private void loadEnvironment(Long id) {
		Intent i = new Intent(this, ArActivity.class);
		i.putExtra(KEY_ENVIRONMENT_ID, id);
		startActivity(i);
		ArActivity.this.finish();
	}

	private void saveToFile(QuadTree floorPlanData, String name) {
		FileOutputStream fout;
		ObjectOutputStream oos;
		try {
			fout = openFileOutput(name + ".tree", Context.MODE_PRIVATE);
			oos = new ObjectOutputStream(fout);
			oos.writeObject(floorPlanData);
			Log.d(TAG, fout.getFD().toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private QuadTree loadFromFile(String name) {
		FileInputStream is;
		ObjectInputStream os;
		QuadTree tree = null;
		try {
			is = openFileInput(name + ".tree");
			os = new ObjectInputStream(is);
			tree = (QuadTree) os.readObject();
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return tree;
	}

	private void setRecognizingSnackbar(boolean enabled){
		if(enabled){
			recognzingSnackbar = Snackbar.make(uxLayout, R.string.recognizing_area_explaination, Snackbar.LENGTH_INDEFINITE);
			recognzingSnackbar.show();
		} else {
			if(recognzingSnackbar != null){
				recognzingSnackbar.dismiss();
			}
		}
	}

	public void hideProgressBar() {
		if(dialog != null){
			dialog.dismiss();
		}
	}

	public void showProgressBar(String msg) {
		dialog = new LoadingDialogFragment();
		dialog.setMessage(msg);
		dialog.show(getFragmentManager(),"progress_dialog");
	}

	private void message(final int message_resource) {
		Snackbar.make(uxLayout,message_resource,Snackbar.LENGTH_SHORT).show();
	}

	protected void setupCameraProperties(Tango tango) {
		extrinsics = setupExtrinsics(tango);
		intrinsics = tango.getCameraIntrinsics(ACTIVE_CAMERA_INTRINSICS);
	}

	protected void connectTango() {
		tango = new Tango(this, new Runnable() {
			@Override
			public void run() {
				synchronized (this) {
					try {
						TangoSupport.initialize();
						TangoConfig config = getTangoConfig();
						tango.connect(config);
						TangoUx.StartParams params = new TangoUx.StartParams();
						tangoUx.start(params);
						ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<>();
						framePairs.add(SOS_T_DEVICE_FRAME_PAIR);
						framePairs.add(DEVICE_T_PREVIOUS_FRAME_PAIR);
						framePairs.add(ADF_T_DEVICE_FRAME_PAIR);
						framePairs.add(ADF_T_SOS_FRAME_PAIR);
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
						message(R.string.exception_out_of_date);
					} catch (TangoErrorException e) {
						Log.e(TAG, getString(R.string.exception_tango_error), e);
						message(R.string.exception_tango_error);
					} catch (TangoInvalidException e) {
						Log.e(TAG, getString(R.string.exception_tango_invalid), e);
						message(R.string.exception_tango_invalid);
					}
				}
			}
		});
		if(tree != null){
			renderer.setFloorLevel(mapper.getFloorLevel());
		}
	}


	@NonNull
	private TangoConfig getTangoConfig() {
		TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
		config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
		config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
		config.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);
		if (adfuuid.equals("")) {
			config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
			config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);
			config.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, LEARNINGMODE_ENABLED);
		} else {
			config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, false);
			config.putString(TangoConfig.KEY_STRING_AREADESCRIPTION, adfuuid);
		}
		return config;
	}

	public TangoPoseData getCurrentSOSPose(){
		return tango.getPoseAtTime(rgbFrameTimestamp, SOS_T_DEVICE_FRAME_PAIR);
	}
	public TangoPoseData getCurrentADFPose() {
		return tango.getPoseAtTime(rgbFrameTimestamp, ADF_T_DEVICE_FRAME_PAIR);
	}


	@Override
	public boolean onTouch(View view, MotionEvent motionEvent) {
		if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
			float u,v;
			if(MappingUtils.getDeviceDefaultOrientation(this) == Configuration.ORIENTATION_LANDSCAPE){
				u = motionEvent.getX() / view.getWidth();
				v = motionEvent.getY() / view.getHeight();
			} else {
				u = 1.0f - motionEvent.getY() / view.getHeight();
				v = motionEvent.getX() / view.getWidth();
			}

			try {
				synchronized (this) {
					float[] touchPosition = getDepthAtTouchPosition(mCurrentImageBuffer, mDisplayRotation, u,v , mPointCloudManager.getLatestPointCloud());

					if (touchPosition != null) {
						switch (currentState){
							case mapping:
								mapper.setFloorLevel(touchPosition[1]);
								message(R.string.floorSet);
								Log.d(TAG, "Floor level: " + mapper.getFloorLevel());
								renderer.setFloorLevel(mapper.getFloorLevel());
//								TODO render Floorplan
								break;
							case editing:
								message(R.string.toggle_quadrant);
								mapper.toggle(new Vector2(touchPosition[0],touchPosition[2]));
								renderer.setTrackPosition(new Vector3(touchPosition[0],touchPosition[1],touchPosition[2]));
								break;
						}
					}
				}

			} catch (TangoException t) {
				message(R.string.failed_measurement);
				Log.e(TAG, getString(R.string.failed_measurement), t);
			} catch (SecurityException t) {
				message(R.string.failed_permissions);
				Log.e(TAG, getString(R.string.failed_permissions), t);
			}
		}
		return true;
	}

	private void setDisplayRotation() {
		Display display = getWindowManager().getDefaultDisplay();
		mDisplayRotation = display.getRotation();
	}

	@Override
	public void onEnvironmentSelected(EnvironmentDAO environment) {
		loadEnvironment(environment.getId());
	}

	private boolean checkAndRequestPermissions() {
		if (!(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
				PackageManager.PERMISSION_GRANTED)) {
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
				final AlertDialog dialog = new AlertDialog.Builder(this)
						.setMessage("AR Navigation requires camera permission")
						.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {
								ActivityCompat.requestPermissions(ArActivity.this,
										new String[]{Manifest.permission.CAMERA}, 0);
							}
						})
						.create();
				dialog.show();
			} else {
				ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
						0);
			}
			return false;
		}
		return true;
	}

	private class mTangoUpdateListener extends Tango.TangoUpdateCallback {

		private long lastPointcloudTimestamp = 0l;
		DescriptiveStatistics pointCloudIntervalls = new DescriptiveStatistics(1000);

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
			if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE
					&& pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE){
				if(environment_id != 0){
					if(!motivating) {
						final Vector3 position = ScenePoseCalculator.
								toOpenGlCameraPose(pose, extrinsics).getPosition();
						new Timer().schedule(new TimerTask(){
							@Override
							public void run() {
								if(!localized){
									renderer.startMotivation(position.z);
								}
							}
						}, motivationStartDelay);
						motivating = true;
					}
				}
			} else if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
					&& pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {
				// Handle new ADF Pose
			} else if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
					&& pose.targetFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE) {
				if(!localized){
					new Timer().schedule(new TimerTask() {
						@Override
						public void run() {
							renderer.onLocalized();
							onInitialLocalization();
						}
					}, motivationEndDelay * 1000);
					Log.d(TAG,"Initial Localization");
				}
				localized = true;
			}
		}
		@Override
		public void onPointCloudAvailable(final TangoPointCloudData pointCloud) {
			if (tangoUx != null) {
				tangoUx.updatePointCloud(pointCloud);
			}
			mPointCloudManager.updatePointCloud(pointCloud);
			newPointcloud = true;
			if(!mapper.isRunning() && capturePointcloud){
				mapper.mapPointCloud(pointCloud);
			}
			if(lastPointcloudTimestamp != 0.0){
				long t = System.currentTimeMillis() - lastPointcloudTimestamp;
				pointCloudIntervalls.addValue(t);
				Log.d(TAG,String.format("Time since last pointcloud: %1$d avg: %2$.2f",t, pointCloudIntervalls.getMean()));
			}
			lastPointcloudTimestamp =  System.currentTimeMillis();
		}

	}

	private class OnSaveButtonClickListener implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			capturePointcloud = false;
			final double finalFloorLevel = mapper.getFloorLevel();
			new AsyncTask<Object, Integer, Long>() {
				@Override
				protected void onPreExecute() {
					showProgressBar("Saving environment");
					fabSave.hide();
					fabPause.hide();
					Log.d(TAG, "Saving environment");
				}

				@Override
				protected Long doInBackground(Object... params) {
					long treeId = 0l;
					String uuid = tango.saveAreaDescription();
					publishProgress(1);
					saveToFile(renderer.getFloorPlanData(), uuid);
					publishProgress(2);
					Log.d(TAG, "Saved ADF");
					EnvironmentDAO environmentDAO = new EnvironmentDAO(uuid, treeId, finalFloorLevel);
					environmentDAO.save();
					publishProgress(3);
					Log.d(TAG, "Saved environment");
					return environmentDAO.getId();
				}

				@Override
				protected void onProgressUpdate(Integer... values) {
					super.onProgressUpdate(values);
					progressBar.setProgress(values[0]);
				}

				@Override
				protected void onPostExecute(Long id) {
					hideProgressBar();
					Log.d(TAG, "Saved environment with id :" + id);
					loadEnvironment(id);
				}
			}.execute();
		}
	}

	private class MyPreFrameCallbackAdapter extends ScenePreFrameCallbackAdapter {
		@Override
		public void onPreFrame(long sceneTime, double deltaTime) {
			synchronized (ArActivity.this) {
				try {

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
						TangoPoseData currentPose;
						if(localized){
							currentPose = getCurrentADFPose();
						} else {
							currentPose = getCurrentSOSPose();
						}
						if (currentPose != null && currentPose.statusCode == TangoPoseData.POSE_VALID) {
							renderer.updateRenderCameraPose(currentPose, extrinsics);
							Vector3 position = ScenePoseCalculator.toOpenGlCameraPose(currentPose, extrinsics).getPosition();
							mapView.setCurrentPosition(position);
							cameraPoseTimestamp = currentPose.timestamp;
						}
					}
					if (newPointcloud) {
						// Update point cloud data.
						TangoPointCloudData pointCloud = mPointCloudManager.getLatestPointCloud();
						if (pointCloud != null) {
							// Calculate the camera color pose at the camera frame update time in
							// OpenGL engine.
							TangoSupport.TangoMatrixTransformData transform =
									TangoSupport.getMatrixTransformAtTime(pointCloud.timestamp,
											TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
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
					if (newQuadtree) {
						if(mapper != null){
							renderer.setFloorLevel(mapper.getFloorLevel());
						}
						mapView.setFloorPlanData(newMapData);
						renderer.setQuadTree(newMapData);

						newQuadtree = false;
					}
					if(togglePointcloud){
						renderer.showPointCloud(!renderer.getRenderPointCloud());
						togglePointcloud = false;
					}
					if(toggleFloorplan){
						renderer.toggleFloorPlan();
						toggleFloorplan = false;
					}
//					if(updatePOIs){
					// Show all POIs
//						List<PoiDAO> poiDAOs = PoiDAO.find(PoiDAO.class, "environment_id = ?", String.valueOf(environment_id));
//						renderer.showAllPOIs(poiDAOs);
//						updatePOIs = false;
//					}
				} catch (TangoInvalidException e){
					Log.d(TAG,e.getMessage(),e);
				}
			}
		}
	}

}
