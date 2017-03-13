package de.stetro.tango.arnavigation.data;


import org.rajawali3d.math.vector.Vector2;
import org.rajawali3d.math.vector.Vector3;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class QuadTree implements Serializable, Cloneable{

    public static final double PLANE_SPACER = 0.02;
    public static final int OBSTACLE_THRESHOLD = 100;
    private static final String TAG = QuadTree.class.getSimpleName();

    private transient Vector2 position;
    private double x;
    private double y;

    private final double halfRange;
    private final int depth;
    private final double range;

    private boolean filled = false;
    private boolean obstacle = false;

    private QuadTree[] children = new QuadTree[4];
    private transient QuadTreeDataListener listener;
    private transient long numObstaclePoints = 0;

    public QuadTree(Vector2 position, double range, int depth) {
        this.position = position;
        this.x = position.getX();
        this.y = position.getY();
        this.halfRange = range / 2.0;
        this.depth = depth;
        this.range = range;
    }

    public QuadTree clone(){
        QuadTree clone = new QuadTree(getVector2(), range, depth);
        clone.setFilled(filled);
        clone.setObstacle(obstacle);
        clone.setListener(listener);
        QuadTree[] cloneChildren = new QuadTree[4];
        for(int i=0;i<4;i++){
            if(children[i] != null){
                cloneChildren[i] = children[i].clone();
            }
        }
        clone.setChildren(cloneChildren);
        return clone;
    }

    public Vector2 getPosition() {
        return getVector2();
    }

    public int getDepth() {
        return depth;
    }

    public double getRange() {
        return range;
    }

    public boolean isFilled() {
        return filled;
    }

    public boolean isObstacle() {
        return obstacle;
    }

    public QuadTree[] getChildren() {
        return children;
    }

    public void setFilled(boolean filled) {
        this.filled = filled;
    }

    public void setObstacle(boolean obstacle){
        this.obstacle = obstacle;
    }

    public void setChildren(QuadTree[] children) {
        this.children = children;
    }

    public List<Vector2> getFilledEdgePointsAsPolygon() {
        ArrayList<Vector2> list = new ArrayList<>();
        getFilledEdgePointsAsPolygon(list);
        return list;
    }

    private void getFilledEdgePointsAsPolygon(ArrayList<Vector2> list) {
        if (filled || (depth == 0 && filled) ) {
            list.add(new Vector2(x, y));
            list.add(new Vector2(x + range - PLANE_SPACER, y));
            list.add(new Vector2(x, y + range - PLANE_SPACER));

            list.add(new Vector2(x, y + range - PLANE_SPACER));
            list.add(new Vector2(x + range - PLANE_SPACER, y));
            list.add(new Vector2(x + range - PLANE_SPACER, y + range - PLANE_SPACER));
        } else {
            for (QuadTree child : children) {
                if (child != null) {
                    child.getFilledEdgePointsAsPolygon(list);
                }
            }
        }
    }

    public List<Vector2> getFilledPoints() {
        ArrayList<Vector2> list = new ArrayList<>();
        getFilledPoints(list);
        return list;
    }

    private void getFilledPoints(ArrayList<Vector2> list) {
        if (depth == 0 && filled) {
            list.add(getVector2());
        } else {
            for (QuadTree child : children) {
                if (child != null) {
                    child.getFilledPoints(list);
                }
            }
        }
    }

    public boolean setFilledInvalidate(Vector2 point) {
        if (!isFilled(point)) {
            setFilled(point);
            if(listener != null){
                listener.OnQuadTreeUpdate();
            }
            return true;
        }
        return false;
    }

    public boolean setFilledInvalidate3(List<Vector3> points){
        List<Vector2> result = new LinkedList<>();
        for(Vector3 p : points){
            result.add(new Vector2(p.x,p.z));
        }
        return setFilledInvalidate(result);
    }

    public boolean setFilledInvalidate(List<Vector2> points){
        boolean updateListener = false;
        for(Vector2 v: points){
            if(!isFilled(v)){
                setFilled(v);
                updateListener = true;
            }
        }
        if(updateListener){
            listener.OnQuadTreeUpdate();
            return true;
        }
        return false;
    }

    public void setListener(QuadTreeDataListener listener) {
        this.listener = listener;
    }

    public void setFilled(Vector2 point) {
        if (depth == 0) {
            if(!obstacle)
                filled = true;
        } else {
            int index = getChildIndex(point);
            if (children[index] == null) {
                children[index] = new QuadTree(getChildPositionByIndex(index), halfRange, depth - 1);
            }
            children[index].setFilled(point);
            this.setFilledIfChildrenAreFilled();
        }
    }

    public void setObstacle(List<Vector3> points){
        for(Vector3 p : points){
            setObstacle(new Vector2(p.x,p.z));
        }
    }

    public void setObstacle(Vector2 point){
        if(depth == 0){
            numObstaclePoints ++;
            if(!obstacle && numObstaclePoints > OBSTACLE_THRESHOLD){
                obstacle = true;
                filled = false;
            }
        } else {
            int index = getChildIndex(point);
            if (children[index] == null) {
                children[index] = new QuadTree(getChildPositionByIndex(index), halfRange, depth - 1);
            }
            children[index].setObstacle(point);
        }
    }

    private Vector2 getChildPositionByIndex(int index) {
        switch (index) {
            case 0:
                return new Vector2(x, y);
            case 1:
                return new Vector2(x, y + halfRange);
            case 2:
                return new Vector2(x + halfRange, y);
            default:
                return new Vector2(x + halfRange, y + halfRange);
        }
    }

    private int getChildIndex(Vector2 point) {
        if (point.getX() < x + halfRange) {
            if (point.getY() < y + halfRange) {
                return 0;
            } else {
                return 1;
            }
        } else {
            if (point.getY() < y + halfRange) {
                return 2;
            } else {
                return 3;
            }
        }
    }

    public void clear() {
        if (depth == 0) {
            filled = false;
            numObstaclePoints = 0;
        } else {
            for (QuadTree child : children) {
                if (child != null) {
                    child.clear();
                }
            }
        }
    }

    public void clearObstacleCount(){
        if (depth == 0) {
            numObstaclePoints = 0;
        } else {
            for (QuadTree child : children) {
                if (child != null) {
                    child.clearObstacleCount();
                }
            }
        }
    }

    public boolean isFilled(Vector2 to) {
        if (outOfRange(to)) {
            return false;
        } else if (depth == 0 || filled) {
            return filled;
        } else {
            int index = getChildIndex(to);
            return children[index] != null && children[index].isFilled(to);
        }
    }

    private boolean outOfRange(Vector2 to) {
        return to.getX() > x + range ||
                to.getX() < x ||
                to.getY() > y + range ||
                to.getY() < y;
    }

    public double getUnit() {
        return range / (Math.pow(2, depth));
    }

    public Vector2 rasterize(Vector2 a) {
        if (depth == 0) {
            return getVector2();
        } else {
            int index = getChildIndex(a);
            if (children[index] != null) {
                return children[index].rasterize(a);
            }
        }
        return a;
    }

    public void forceFilled(Vector2 v) {
        if(depth == 0){
            filled = true;
            obstacle = true;
            if(listener != null){
                listener.OnQuadTreeUpdate();
            }
        } else {
            int index = getChildIndex(v);
            if (children[index] == null) {
                children[index] = new QuadTree(getChildPositionByIndex(index), halfRange, depth - 1);
            }
            children[index].forceFilled(v);
        }
    }

    public boolean setFilledIfChildrenAreFilled(){
        if(depth == 0){
            return filled;
//        } else if(depth == 1){
//            int n = 0;
//            for(QuadTree c : children){
//                if(c != null && c.filled){
//                    n++;
//                }
//            }
//            return n>=3;
        } else {
            boolean f = true;
            for(QuadTree c : children){
                if(c != null && f){
                    f = f && c.filled;
                } else {
                    return false;
                }
            }
            return this.filled = f;
        }
    }

    private Vector2 getVector2(){
        if(position == null){
            position = new Vector2(x,y);
        }
        return position;
    }

    public interface QuadTreeDataListener {

        void OnQuadTreeUpdate();
    }
}
