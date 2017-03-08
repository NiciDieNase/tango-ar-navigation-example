package de.stetro.tango.arnavigation.data.persistence;

import android.util.Log;

import com.orm.SugarRecord;

import org.rajawali3d.math.vector.Vector2;

import java.util.List;

import de.stetro.tango.arnavigation.data.QuadTree;

/**
 * Created by felix on 06/03/17.
 */

public class QuadTreeDAO extends SugarRecord{

    private static final String TAG = QuadTreeDAO.class.getSimpleName();
    private double x;
    private double y;
    private int depth;
    private boolean filled;
    private boolean obstacle;
    private double range;
    private int childIndex;
    private long parentId;

    public QuadTreeDAO(){}

    public QuadTreeDAO(double x, double y, int depth, double range, boolean filled, boolean obstacle, int childIndex, long parentId) {
        this.x = x;
        this.y = y;
        this.depth = depth;
        this.filled = filled;
        this.obstacle = obstacle;
        this.childIndex = childIndex;
        this.parentId = parentId;
    }

    public static long persist(QuadTree node){
        return persisit(node,0,0);
    }

    private static long persisit(QuadTree node, int childIndex, long parentId){
        Vector2 position = node.getPosition();
        QuadTreeDAO newNode = new QuadTreeDAO(position.getX(),position.getY(),node.getDepth(),node.getRange(),node.isFilled(),node.isObstacle(),childIndex,parentId);
        newNode.save();
        QuadTree[] children = node.getChildren();
        if(node.getDepth() > 0){
            for(int i = 0; i < children.length; i++ ){
                if(children[i] != null){
                    persisit(children[i],i,newNode.getId());
                }
            }
        }
        return newNode.getId();
    }

    public static QuadTree loadTreeFromRootNode(long id){
        QuadTreeDAO rootDAO = QuadTreeDAO.findById(QuadTreeDAO.class, id);
        QuadTree root = getObjectFromDAO(rootDAO);
        recursiveLoad(rootDAO,root);
        return root;
    }

    private static void recursiveLoad(QuadTreeDAO dao, QuadTree node){
        loadChildren(dao,node);
        for(QuadTreeDAO daoChild :dao.getChildren()){
            recursiveLoad(daoChild,node.getChildren()[daoChild.getChildIndex()]);
        }
    }

    private static QuadTree loadChildren(QuadTreeDAO dao, QuadTree node){
        List<QuadTreeDAO> childrenDAOs = dao.getChildren();
        QuadTree[] children = new QuadTree[4];
        for(QuadTreeDAO childDAO: childrenDAOs){
            int childIndex = childDAO.getChildIndex();
            if(0 <= childIndex && childIndex < 4){
                children[childIndex] = getObjectFromDAO(childDAO);
            } else {
                Log.d(TAG,"Index out of Range");
            }
        }
        node.setChildren(children);
        return node;
    }

    private static QuadTree getObjectFromDAO(QuadTreeDAO dao){
        QuadTree quadTree = new QuadTree(new Vector2(dao.getX(),dao.getY()), dao.getRange(), dao.getDepth());
        quadTree.setFilled(dao.isFilled());
        quadTree.setObstacle(dao.isObstacle());
        return quadTree;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public double getRange() {
        return range;
    }

    public void setRange(double range) {
        this.range = range;
    }

    public boolean isFilled() {
        return filled;
    }

    public void setFilled(boolean filled) {
        this.filled = filled;
    }

    public boolean isObstacle() {
        return obstacle;
    }

    public void setObstacle(boolean obstacle) {
        this.obstacle = obstacle;
    }

    public int getChildIndex() {
        return childIndex;
    }

    public void setChildIndex(int childIndex) {
        this.childIndex = childIndex;
    }


    public List<QuadTreeDAO> getChildren(){
        return QuadTreeDAO.find(QuadTreeDAO.class, "parent_id = ?", String.valueOf(this.parentId));
    }
}
