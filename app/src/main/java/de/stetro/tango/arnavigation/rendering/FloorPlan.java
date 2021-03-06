package de.stetro.tango.arnavigation.rendering;


import android.util.Log;

import org.rajawali3d.Object3D;
import org.rajawali3d.materials.Material;
import org.rajawali3d.math.vector.Vector2;
import org.rajawali3d.math.vector.Vector3;

import java.nio.FloatBuffer;
import java.util.List;

import de.stetro.tango.arnavigation.data.EnvironmentMapper;
import de.stetro.tango.arnavigation.data.QuadTree;


public class FloorPlan extends Object3D {

    private static final int MAX_VERTICES = (int) (Math.pow(4, EnvironmentMapper.QUAD_TREE_DEPTH) * 4);
    // 4^9 is the max # of leafes in our quadtree, and for each node we need 4 vertices

    private static final String TAG = FloorPlan.class.getSimpleName();
    private final float[] color;
    private QuadTree data;
    private double floorLevel = -1.4;

    public FloorPlan(QuadTree data) {
        super();
        this.color = new float[]{0.0f, 1.0f, 0.0f, 0.5f};
        this.data = data;
        init();
    }

    public void setTrajectoryPosition(Vector3 position) {
        if(this.addPoint(position))
            this.rebuildPoints();
    }

    public void bulkAdd(List<Vector3> floorPoints, List<Vector3> obstaclePoints){
        data.setObstacle(obstaclePoints);
        data.setFilledInvalidate3(floorPoints);
        rebuildPoints();
        data.clearObstacleCount();
    }

    public void bulkAdd(List<List<Vector3>> positions){
        bulkAdd(positions.get(0),positions.get(1));
    }

    protected boolean addPoint(Vector3 point) {
        Vector2 p = new Vector2(point.x, point.z);
        return data.setFilledInvalidate(p);
    }

    public void rebuildPoints() {
        List<Vector2> filledPoints = data.getFilledEdgePointsAsPolygon();
        Log.d(TAG, String.format("%1$d Vertices left", MAX_VERTICES - filledPoints.size()*3));
//        if(filledPoints.size() * 3 > MAX_VERTICES){
//            throw new RuntimeException("To many tiles");
//        }
        FloatBuffer points = FloatBuffer.allocate(filledPoints.size() * 3);
        for (Vector2 filledPoint : filledPoints) {
            points.put((float) filledPoint.getX());
            points.put(0);
            points.put((float) filledPoint.getY());
        }
//        updatePoints(filledPoints.size() * 6 / 4, points);
        updatePoints(filledPoints.size(), points);
    }

    private void init() {
        this.setTransparent(true);
        this.setDoubleSided(true);

        float[] vertices = new float[MAX_VERTICES * 3];
        float[] normals = new float[MAX_VERTICES * 3];
        int[] indices = new int[MAX_VERTICES];
        for (int i = 0; i < indices.length; ++i) {
            switch (i%6){
                case 0:
                    indices[i] = 0;
                    break;
                case 1:
                case 4:
                    indices[i] = 1;
                    break;
                case 2:
                case 3:
                    indices[i] = 2;
                    break;
                case 5:
                    indices[i] = 3;
            }
            indices[i] += (i/6)*4;
            int index = i * 3;
            normals[index] = 0;
            normals[index + 1] = 1;
            normals[index + 2] = 0;
        }
        setData(vertices, normals, null, null, indices, false);
        Material material = new Material();
        material.setColor(color);
        setMaterial(material);
        rebuildPoints();
        setPosition(new Vector3(0, floorLevel, 0));
    }

    private void updatePoints(int pointCount, FloatBuffer pointCloudBuffer) {
//        if(pointCount <= 0){
//            return;
//        }
//        float[] vertices = new float[pointCount * 3];
//        float[] normals = new float[pointCount * 6 / 4];
//        int[] indices = new int[pointCount * 6 / 4];
//        for (int i = 0; i < indices.length; i++) {
//            switch (i%6){
//                case 0:
//                    indices[i] = 0;
//                    break;
//                case 1:
//                case 4:
//                    indices[i] = 1;
//                    break;
//                case 2:
//                case 3:
//                    indices[i] = 2;
//                    break;
//                case 5:
//                    indices[i] = 3;
//            }
//            indices[i] += (i/6)*4;
//            if(i % 3 == 1){
//                normals[i] = 1;
//            } else {
//                normals[i] = 0;
//            }
//        }
//        pointCloudBuffer.position(0);
//        pointCloudBuffer.get(vertices);
//        Log.d(TAG,vertices.length + " " + normals.length + " " + indices.length);

//        setData(vertices,normals,null,null,indices,false);
//        mGeometry.setNumIndices(indices.length);
//        mGeometry.setVertices(vertices);
//        mGeometry.changeBufferData(mGeometry.getVertexBufferInfo(), mGeometry.getVertices(), 0, pointCount * 3);
        mGeometry.setNumIndices(pointCount * 6 /4);
        mGeometry.setVertices(pointCloudBuffer);
        mGeometry.changeBufferData(mGeometry.getVertexBufferInfo(), mGeometry.getVertices(), 0, pointCount * 3 * 6 / 4);
    }

    public QuadTree getData() {
        return data;
    }

    public double getFloorLevel() {
        return floorLevel;
    }

    public void setFloorLevel(double floorLevel) {
        this.floorLevel = floorLevel;
    }

    public void forceAdd(Vector3 v){
        data.forceFilled(new Vector2(v.x,v.z),true);
        this.rebuildPoints();
    }

    public void setData(QuadTree data) {
        this.data = data;
        rebuildPoints();
    }
}
