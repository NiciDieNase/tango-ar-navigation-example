package de.stetro.tango.arnavigation.rendering;


import android.content.Context;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.MotionEvent;
import android.view.animation.LinearInterpolator;

import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.examples.java.pointcloud.rajawali.PointCloud;
import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.ScenePoseCalculator;

import org.rajawali3d.Object3D;
import org.rajawali3d.animation.Animation;
import org.rajawali3d.animation.Animation3D;
import org.rajawali3d.animation.IAnimationListener;
import org.rajawali3d.animation.RotateOnAxisAnimation;
import org.rajawali3d.animation.TranslateAnimation3D;
import org.rajawali3d.curves.CatmullRomCurve3D;
import org.rajawali3d.lights.ALight;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.lights.PointLight;
import org.rajawali3d.loader.LoaderOBJ;
import org.rajawali3d.loader.ParsingException;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.methods.SpecularMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector2;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Cylinder;
import org.rajawali3d.primitives.Line3D;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.primitives.Sphere;
import org.rajawali3d.renderer.RajawaliRenderer;
import org.rajawali3d.scene.RajawaliScene;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;

import javax.microedition.khronos.opengles.GL10;

import de.stetro.tango.arnavigation.R;
import de.stetro.tango.arnavigation.data.NoPathException;
import de.stetro.tango.arnavigation.data.PathFinder;
import de.stetro.tango.arnavigation.data.QuadTree;
import de.stetro.tango.arnavigation.data.persistence.PoiDAO;


public class SceneRenderer extends RajawaliRenderer {
    private static final int QUAD_TREE_START = -60;
    private static final int QUAD_TREE_RANGE = 120;
    private static final String TAG = SceneRenderer.class.getSimpleName();
    private static final int MAX_NUMBER_OF_POINTS = 60000;
    private static final double CLEAR_DISTANCE = .8;
    private double RANDOM_RANGE = 15.0;
    private static final int NUM_RANDOM_OBJECTS = 40;
    private QuadTree data;
    // Rajawali texture used to render the Tango color camera
    private ATexture mTangoCameraTexture;

    private FloorPlan floorPlan;
    // Keeps track of whether the scene camera has been configured
    private boolean mSceneCameraConfigured;
    private Material blue;
    private Material green;
    private Material red;
    private Material yellow;
    private PointCloud mPointCloud;

    private Sphere TrackPoint;
    private Vector3 startPoint;
    private Vector3 endPoint;
    private List<Object3D> pathObjects = new ArrayList<>();
    private boolean renderPath = false;
    private boolean renderPointCloud = true;
    private boolean renderFloorPlan = false;
    private boolean renderSpheres = false;
    private boolean renderCoins = true;
    private Cylinder PointOfInterest;
    private boolean renderPOI = false;
    private List<Object3D> POIs = new ArrayList<>();
    private PointLight light;
    private double pathHeight;
    private RotateOnAxisAnimation anim;
    private Object3D mCoin;
    private List<Animation3D> pathAnimations = new ArrayList<>();
    private List<Animation3D> motivateAnimations = new ArrayList<>();
    private LoaderOBJ objParser;

    private OnRoutingErrorListener listerner;
    private Line3D path;
    private List<Object3D> motivationObjects = new ArrayList<>();
    private boolean startMotivation;
    private TargetMarker mTargetMarker;
    private ALight spot;
    private boolean finishMotivation;

    private boolean motivationEnabled = false;
    private boolean pathEnabled = false;
    private boolean floorplanEnabled = false;

    public void toggleFloorPlan() {
        this.renderFloorPlan = !this.renderFloorPlan;
    }

    public interface OnRoutingErrorListener{
        void onRoutingError(int resId);
    }
    public SceneRenderer(Context context) {
        super(context);
        data = new QuadTree(new Vector2(QUAD_TREE_START, QUAD_TREE_START), QUAD_TREE_RANGE, 9);
    }

    public SceneRenderer(Context context, QuadTree data){
        super(context);
        this.data = data;
    }

    public void setQuadTree(QuadTree data){
        this.data = data;
        floorPlan.setData(data);
    }

    @Override
    protected void initScene() {
        // Create a quad covering the whole background and assign a texture to it where the
        // Tango color camera contents will be rendered.
        ScreenQuad backgroundQuad = new ScreenQuad();
        Material tangoCameraMaterial = new Material();
        tangoCameraMaterial.setColorInfluence(0);
        // We need to use Rajawali's {@code StreamingTexture} since it sets up the texture
        // for GL_TEXTURE_EXTERNAL_OES rendering
        mTangoCameraTexture =
                new StreamingTexture("camera", (StreamingTexture.ISurfaceListener) null);
        try {
            tangoCameraMaterial.addTexture(mTangoCameraTexture);
            backgroundQuad.setMaterial(tangoCameraMaterial);
        } catch (ATexture.TextureException e) {
            Log.e(TAG, "Exception creating texture for RGB camera contents", e);
        }
        getCurrentScene().addChildAt(backgroundQuad, 0);


        blue = new Material();
        blue.setColor(Color.BLUE);

        green = new Material();
        green.setColor(Color.GREEN);

        yellow = new Material();
        yellow.setColor(Color.YELLOW);
        yellow.enableLighting(true);
        yellow.setColorInfluence(1.0f);
//        yellow.setAmbientColor(Color.YELLOW);
        yellow.setDiffuseMethod(new DiffuseMethod.Lambert());
        yellow.setSpecularMethod(new SpecularMethod.Phong());

        red = new Material();
        red.setColor(Color.RED);
        red.enableLighting(true);
        red.setDiffuseMethod(new DiffuseMethod.Lambert());
        red.setSpecularMethod(new SpecularMethod.Phong());
        red.setColorInfluence(0.5f);
//        red.setAmbientColor(new float[]{0.8f, 0.1f, 0.1f});

        light = new PointLight();
        light.setColor(1, 1, 1);
        light.setPower(0.8f);
        getCurrentScene().addLight(light);

        spot = new DirectionalLight(0, 1, 0);
//        spot = new PointLight();
        spot.setColor(1, 1, 1);
        spot.setPower(1.0f);
        getCurrentScene().addLight(light);


        floorPlan = new FloorPlan(data);
        getCurrentScene().addChild(floorPlan);
        floorPlan.setVisible(renderFloorPlan);

        mPointCloud = new PointCloud(MAX_NUMBER_OF_POINTS, 4);
        getCurrentScene().addChild(mPointCloud);
        mPointCloud.setVisible(renderPointCloud);

        TrackPoint = new Sphere(0.05f,20,20);
        TrackPoint.setVisible(false);
        TrackPoint.setMaterial(tangoCameraMaterial);
        getCurrentScene().addChild(TrackPoint);

        objParser = new LoaderOBJ(mContext.getResources(),mTextureManager,R.raw.coin_n5_obj);
        try {
            objParser.parse();
            mCoin = objParser.getParsedObject();
            mCoin.setScale(10.0);
            mCoin.setDoubleSided(true);
            mCoin.setVisible(false);
            getCurrentScene().addChild(mCoin);
        } catch (ParsingException e) {
            e.printStackTrace();
        }
//        setRotateAnimation(mCoin);

        mTargetMarker = new TargetMarker(1.2f,0.4f);
        setRotateAnimation(mTargetMarker).play();
        getCurrentScene().addChild(mTargetMarker);
        mTargetMarker.setPosition(0,200,0);
        mTargetMarker.setVisible(false);
        mTargetMarker.setMaterial(red);

    }

    public void startMotivation(double height) {
        if(motivationEnabled){
            for(int i = 0; i< NUM_RANDOM_OBJECTS ; i++){
                double x = (Math.random() * RANDOM_RANGE) - RANDOM_RANGE /2;
                double z = (Math.random() * RANDOM_RANGE) - RANDOM_RANGE /2;
                Object3D randomObject = mCoin.clone(true,true);
                randomObject.setPosition(x,height,z);
                Animation3D animation3D = setRotateAnimation(randomObject);
                motivateAnimations.add(animation3D);
                animation3D.play();
                motivationObjects.add(randomObject);
            }
            startMotivation = true;
        }
    }

    public void finishMotivation(){
        if(motivationEnabled) {
            final List<Animation3D> removeAnimations = new ArrayList<>();
            for (int i = 0; i < motivationObjects.size(); i++) {
                Object3D obj = motivationObjects.get(i);
                Animation3D animation = setRemoveAnimation(obj);
                animation.setDelayDelta(i * 0.25);
                animation.play();
            }
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    RajawaliScene scene = getCurrentScene();
                    for (Animation3D anim : motivateAnimations) {
                        scene.unregisterAnimation(anim);
                    }
                    for (Animation3D anim : removeAnimations) {
                        scene.unregisterAnimation(anim);
                    }
                    motivateAnimations.clear();
                    removeAnimations.clear();
                }
            }, (long) (motivationObjects.size() * 0.3 * 1000));
        }
    }

    /**
     * Update the scene camera based on the provided pose in Tango start of service frame.
     * The device pose should match the pose of the device at the time the last rendered RGB
     * frame, which can be retrieved with this.getTimestamp();
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public void updateRenderCameraPose(TangoPoseData devicePose, DeviceExtrinsics extrinsics) {
        Pose cameraPose = ScenePoseCalculator.toOpenGlCameraPose(devicePose, extrinsics);
        getCurrentCamera().setRotation(cameraPose.getOrientation());
        getCurrentCamera().setPosition(cameraPose.getPosition());
        floorPlan.setTrajectoryPosition(cameraPose.getPosition());
        checkRemovableObjects(cameraPose.getPosition());
        light.setPosition(cameraPose.getPosition());
    }

    private void checkRemovableObjects(Vector3 position) {
        final List<Object3D> removeElements = new ArrayList<>();
        for(final Object3D obj: pathObjects){
            if(horizontalDistance(obj.getPosition(),position) < CLEAR_DISTANCE){
                removeElements.add(obj);
                setRemoveAnimation(obj).play();
            }
        }
        pathObjects.removeAll(removeElements);
        for(Object3D obj: motivationObjects){
            if(horizontalDistance(obj.getPosition(),position) < CLEAR_DISTANCE){
                setRemoveAnimation(obj).play();
            }
        }
        if(horizontalDistance(mTargetMarker.getPosition(),position)< CLEAR_DISTANCE){
            setRemoveAnimation(mTargetMarker);
        }
    }

    private Animation3D setRemoveAnimation(Object3D obj) {
        Vector3 target = obj.getPosition().clone();
        target.y = target.y + 5;
        Animation3D anim = new TranslateAnimation3D(obj.getPosition(),target);
        anim.setTransformable3D(obj);
        anim.setDurationMilliseconds(4000);
        anim.registerListener(new DeletAfterAnimationListener(obj,anim));
        getCurrentScene().registerAnimation(anim);
        return anim;
    }

    /**
     * It returns the ID currently assigned to the texture where the Tango color camera contents
     * should be rendered.
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public int getTextureId() {
        return mTangoCameraTexture == null ? -1 : mTangoCameraTexture.getTextureId();
    }

    /**
     * We need to override this method to mark the camera for re-configuration (set proper
     * projection matrix) since it will be reset by Rajawali on surface changes.
     */
    @Override
    public void onRenderSurfaceSizeChanged(GL10 gl, int width, int height) {
        super.onRenderSurfaceSizeChanged(gl, width, height);
        mSceneCameraConfigured = false;
    }

    public boolean isSceneCameraConfigured() {
        return mSceneCameraConfigured;
    }

    /**
     * Sets the projection matrix for the scene camera to match the parameters of the color camera,
     * provided by the {@code TangoCameraIntrinsics}.
     */
    public void setProjectionMatrix(TangoCameraIntrinsics intrinsics) {
        if(intrinsics != null){
            Matrix4 projectionMatrix = ScenePoseCalculator.calculateProjectionMatrix(
                    intrinsics.width, intrinsics.height,
                    intrinsics.fx, intrinsics.fy, intrinsics.cx, intrinsics.cy);
            getCurrentCamera().setProjectionMatrix(projectionMatrix);
        }
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset,
                                 float xOffsetStep, float yOffsetStep,
                                 int xPixelOffset, int yPixelOffset) {
    }

    @Override
    public void onTouchEvent(MotionEvent event) {

    }


    @Override
    protected void onRender(long ellapsedRealtime, double deltaTime) {
        synchronized (this){
            super.onRender(ellapsedRealtime, deltaTime);
            RajawaliScene scene;
            if(startMotivation){
                scene = getCurrentScene();
                for(Object3D obj: motivationObjects){
                    scene.addChild(obj);
                    motivateAnimations.add(setRotateAnimation(obj));
                }
                startMotivation = false;
            }
            if (renderPath) {
                try {
                    scene = getCurrentScene();
                    PathFinder finder = new PathFinder(floorPlan.getData());
                    // Remove old objects
                    for(Object3D obj:pathObjects){
                        scene.removeChild(obj);
                    }
                    pathObjects.clear();
                    scene.removeChild(path);
                    for(Animation3D anim:pathAnimations){
                        scene.unregisterAnimation(anim);
                    }
                    pathAnimations.clear();

                    // Calculate Path and get intermediate Points
                    List<Vector2> pathBetween = finder.findPathBetween(startPoint, endPoint);

                    CatmullRomCurve3D curvePath = new CatmullRomCurve3D();
                    for (Vector2 vector2 : pathBetween) {
                        curvePath.addPoint(new Vector3(vector2.getX(), floorPlan.getFloorLevel(), vector2.getY() ));
                    }
                    // Calculate distance between Objects placed on path
                    double v1 = 0.8 / (curvePath.getLength(100)/100) ;
                    Log.d(TAG,"Calculated Number of segments: " + v1);
                    int v2 = (int)v1;

                    // Add objects to mark Path
                    Stack linePoints = new Stack();
                    for (int i = 0; i < 100; i++) {
                        Vector3 v = new Vector3();
                        curvePath.calculatePoint(v,i / 100f);
                        linePoints.add(v);
                        if(renderSpheres && i%v2 == 0){
                            Sphere s = new Sphere(0.10f,20,20);
                            s.setPosition(v);
                            s.setY(pathHeight-.3);
                            s.setMaterial(yellow);
                            pathObjects.add(s);
                        }
                        if(renderCoins && i%v2 == 0){
                            Object3D coin = mCoin.clone(true,true);
                            coin.setPosition(v);
                            coin.setScale(10.0);
                            coin.setVisible(true);
                            coin.setY(pathHeight);
                            pathObjects.add(coin);
                            Animation3D anim = setRotateAnimation(coin);
                            pathAnimations.add(anim);
                        }
                    }
                    this.path = new Line3D(linePoints, 10, Color.BLUE);
                    this.path.setMaterial(blue);
                    if(pathEnabled){
                        scene.addChild(this.path);
                    }
                    // Add Objects and start Animations
                    for(Object3D obj:pathObjects){
                        scene.addChild(obj);
                    }
                    for(Animation3D anim: pathAnimations){
                        anim.play();
                    }
                } catch (NoPathException e){
                    if(listerner != null){
                        listerner.onRoutingError(R.string.routing_error);
                    } else {
                        e.printStackTrace();
                    }
                } finally {
                    renderPath = false;
                }
            }
        }
    }

    @NonNull
    private Animation3D setRotateAnimation(Object3D coin) {
        RotateOnAxisAnimation anim = new RotateOnAxisAnimation(Vector3.Axis.Y, 360.0);
        anim.setInterpolator(new LinearInterpolator());
        anim.setDurationMilliseconds(5000);
        anim.setRepeatMode(Animation.RepeatMode.INFINITE);
        anim.setTransformable3D(coin);
        getCurrentScene().registerAnimation(anim);
        return anim;
    }

    public void setPath(Vector3 start, Vector3 end){
        startPoint = start;
        endPoint = end;
        floorPlan.addPoint(start);
        floorPlan.addPoint(end);
        renderPath = true;
    }

    public void setStartPoint(TangoPoseData currentPose, DeviceExtrinsics extrinsics) {
        startPoint = ScenePoseCalculator.toOpenGlCameraPose(currentPose, extrinsics).getPosition();
        floorPlan.addPoint(startPoint);
        if (startPoint != null && endPoint != null) {
            renderPath = true;
        }
    }

    public void setEndPoint(TangoPoseData currentPose, DeviceExtrinsics extrinsics) {
        endPoint = ScenePoseCalculator.toOpenGlCameraPose(currentPose, extrinsics).getPosition();
        floorPlan.addPoint(endPoint);
        if (startPoint != null && endPoint != null) {
            renderPath = true;
        }
    }

    public QuadTree getFloorPlanData() {
        return data;
    }

    public void addToFloorPlan(List<List<Vector3>>positions){
        floorPlan.bulkAdd(positions);
    }

    public void setTrackPosition(Vector3 position){
        TrackPoint.setPosition(position);
    }

    public void setFloorLevel(double level){
        if(floorPlan != null){
            floorPlan.setFloorLevel(level);
        }
    }

    public void updatePointCloud(TangoPointCloudData pointCloudData, float[] openGlTdepth) {
        mPointCloud.updateCloud(pointCloudData.numPoints, pointCloudData.points);
        Matrix4 openGlTdepthMatrix = new Matrix4(openGlTdepth);
        mPointCloud.setPosition(openGlTdepthMatrix.getTranslation());
        // Conjugating the Quaternion is need because Rajawali uses left handed convention.
        mPointCloud.setOrientation(new Quaternion().fromMatrix(openGlTdepthMatrix).conjugate());
    }

    public boolean showPointCloud(boolean show){
        this.renderPointCloud = show;
        mPointCloud.setVisible(renderPointCloud);
        return renderPointCloud;
    }

    public boolean getRenderPointCloud(){
        return renderPointCloud;
    }

    public boolean renderFloorPlan(boolean show){
        this.renderFloorPlan = show;
        if(floorplanEnabled){
            floorPlan.setVisible(renderFloorPlan);
        }
        return renderFloorPlan;
    }

    public void renderSphere(boolean render){
        this.TrackPoint.setVisible(render);
    }

    public boolean getRenderFloorPlan(){ return renderFloorPlan; };

    public void manuelUpdate(float[] point){
        floorPlan.forceAdd(new Vector3(point[0],point[1],point[2]));
    }

    public double getFloorLevel(){
        return floorPlan.getFloorLevel();
    }

    public void updateMapData(QuadTree mapData){
        data = mapData;
    }

    public void showPOI(Vector3 p){
        getCurrentScene().addChild(mTargetMarker);
        mTargetMarker.setPosition(p.x, getFloorLevel(), p.z);
        mTargetMarker.setVisible(true);
        spot.setPosition(p.x, getFloorLevel()-0.3, p.z);
    }

    public void hidePOI(){
        mTargetMarker.setVisible(false);
    }

    public void showAllPOIs(List<PoiDAO> poiDAOs) {
        RajawaliScene scene = getCurrentScene();
        for(Object3D obj: POIs){
            scene.removeChild(obj);
        }
        POIs.clear();
        for(PoiDAO p:poiDAOs){
            Object3D marker = new TargetMarker(1.2f,0.4f);
            marker.setPosition(p.getPosition());
            scene.addChild(marker);
            Animation3D anim = setRotateAnimation(marker);
            scene.registerAnimation(anim);
            anim.play();
            POIs.add(marker);
        }
    }

    public void hideAllPOIs(){
        for(Object3D obj : POIs){
            getCurrentScene().removeChild(obj);
        }
    }

    public void setPathHeight(double pathHeight) {
        this.pathHeight = pathHeight;
    }

    private class DeletAfterAnimationListener implements IAnimationListener {
        private Object3D obj;
        private Animation anim;

        public DeletAfterAnimationListener(Object3D obj,Animation anim) {
            this.obj = obj;
            this.anim = anim;
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            getCurrentScene().removeChild(obj);
            getCurrentScene().unregisterAnimation(anim);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {

        }

        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationUpdate(Animation animation, double interpolatedTime) {

        }
    }

    private double horizontalDistance(Vector3 v1, Vector3 v2){
        v1 = v1.clone();
        v2 = v2.clone();
        v1.y = 0;
        v2.y = 0;
        return v1.distanceTo(v2);
    }

    public void setListerner(OnRoutingErrorListener listerner) {
        this.listerner = listerner;
    }

    public void setMotivationEnabled(boolean motivationEnabled) {
        this.motivationEnabled = motivationEnabled;
    }

    public void setPathEnabled(boolean pathEnabled) {
        this.pathEnabled = pathEnabled;
    }

    public void setFloorplanEnabled(boolean floorplanEnabled) {
        this.floorplanEnabled = floorplanEnabled;
    }
}
