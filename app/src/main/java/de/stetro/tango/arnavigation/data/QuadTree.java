package de.stetro.tango.arnavigation.data;


import org.rajawali3d.math.vector.Vector2;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class QuadTree implements Serializable, Cloneable{

    public static final double PLANE_SPACER = 0.02;
    public static final int OBSTACLE_THRESHOLD = 100;
    private static final String TAG = QuadTree.class.getSimpleName();

    private final Vector2 position;

    private final double halfRange;
    private final int depth;
    private final double range;

    private boolean filled = false;
    private boolean obstacle = false;

    private QuadTree[] children = new QuadTree[4];
    private QuadTreeDataListener listener;
    private long numObstaclePoints = 0;

    public QuadTree(Vector2 position, double range, int depth) {
        this.position = position;
        this.halfRange = range / 2.0;
        this.depth = depth;
        this.range = range;
    }

    public QuadTree clone(){
        QuadTree clone = new QuadTree(new Vector2(position.getX(), position.getY()), range, depth);
        clone.setFilled(filled);
        clone.setObstacle(obstacle);
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
        return position;
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
            list.add(new Vector2(position.getX(), position.getY()));
            list.add(new Vector2(position.getX() + range - PLANE_SPACER, position.getY()));
            list.add(new Vector2(position.getX(), position.getY() + range - PLANE_SPACER));

            list.add(new Vector2(position.getX(), position.getY() + range - PLANE_SPACER));
            list.add(new Vector2(position.getX() + range - PLANE_SPACER, position.getY()));
            list.add(new Vector2(position.getX() + range - PLANE_SPACER, position.getY() + range - PLANE_SPACER));
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
            list.add(position);
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
                return new Vector2(position.getX(), position.getY());
            case 1:
                return new Vector2(position.getX(), position.getY() + halfRange);
            case 2:
                return new Vector2(position.getX() + halfRange, position.getY());
            default:
                return new Vector2(position.getX() + halfRange, position.getY() + halfRange);
        }
    }

    private int getChildIndex(Vector2 point) {
        if (point.getX() < position.getX() + halfRange) {
            if (point.getY() < position.getY() + halfRange) {
                return 0;
            } else {
                return 1;
            }
        } else {
            if (point.getY() < position.getY() + halfRange) {
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
        return to.getX() > position.getX() + range ||
                to.getX() < position.getX() ||
                to.getY() > position.getY() + range ||
                to.getY() < position.getY();
    }

    public double getUnit() {
        return range / (Math.pow(2, depth));
    }

    public Vector2 rasterize(Vector2 a) {
        if (depth == 0) {
            return position;
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

    public interface QuadTreeDataListener {

        void OnQuadTreeUpdate();
    }
}
