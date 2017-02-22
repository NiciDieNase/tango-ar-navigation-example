package de.stetro.tango.arnavigation.rendering;


import org.rajawali3d.Object3D;
import org.rajawali3d.materials.Material;
import org.rajawali3d.math.vector.Vector2;
import org.rajawali3d.math.vector.Vector3;

import java.nio.FloatBuffer;
import java.util.LinkedList;
import java.util.List;

import de.stetro.tango.arnavigation.data.QuadTree;


public class FloorPlan extends Object3D {

    private static final int MAX_VERTICES = 10000;
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

    public void bulkAdd(List<List<Vector3>> positions){
        // TODO this would be the point to handle obstacles
        addObstacle(positions.get(1));
        addPoints(positions.get(0));
        // TODO filter obstacles
        rebuildPoints();
        data.clearObstacleCount();
    }

    protected boolean addPoint(Vector3 point) {
        Vector2 p = new Vector2(point.x, point.z);
        return data.setFilledInvalidate(p);
    }

    protected boolean addPoints(List<Vector3> points){
        List<Vector2> result = new LinkedList<>();
        for(Vector3 p : points){
            result.add(new Vector2(p.x,p.z));
        }
        return data.setFilledInvalidate(result);
    }

    private void addObstacle(List<Vector3> position) {
        for(Vector3 p : position){
            data.setObstacle(new Vector2(p.x,p.z));
        }
    }

    public void rebuildPoints() {
        List<Vector2> filledPoints = data.getFilledEdgePointsAsPolygon();
        FloatBuffer points = FloatBuffer.allocate(filledPoints.size() * 3);
        for (Vector2 filledPoint : filledPoints) {
            points.put((float) filledPoint.getX());
            points.put(0);
            points.put((float) filledPoint.getY());
        }
        updatePoints(filledPoints.size(), points);
    }

    private void init() {
        this.setTransparent(true);
        this.setDoubleSided(true);

        float[] vertices = new float[MAX_VERTICES * 3];
        float[] normals = new float[MAX_VERTICES * 3];
        int[] indices = new int[MAX_VERTICES];
        for (int i = 0; i < indices.length; ++i) {
            indices[i] = i;
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
        mGeometry.setNumIndices(pointCount);
        mGeometry.setVertices(pointCloudBuffer);
        mGeometry.changeBufferData(mGeometry.getVertexBufferInfo(), mGeometry.getVertices(), 0, pointCount * 3);
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

    public void forceAdd(Vector2 v){
        data.forceFilled(v);
    }
}
