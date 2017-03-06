package de.stetro.tango.arnavigation.data.persistence;

import com.orm.SugarRecord;

import org.rajawali3d.math.vector.Vector2;

import de.stetro.tango.arnavigation.data.QuadTree;

/**
 * Created by felix on 06/03/17.
 */

public class QuadTreeDAO extends SugarRecord{

    private double x;
	private double y;
	private int depth;
	private boolean filled;
	private boolean obstacle;
	private double range;
	private int childIndex;
	private QuadTreeDAO parent;

	public QuadTreeDAO(){}

	public QuadTreeDAO(double x, double y, int depth, boolean filled, boolean obstacle, int childIndex, QuadTreeDAO parent) {
		this.x = x;
		this.y = y;
		this.depth = depth;
		this.filled = filled;
		this.obstacle = obstacle;
		this.childIndex = childIndex;
		this.parent = parent;
	}

	public static long persist(QuadTree node){
		return persisit(node,-1,null);
	}

	public static long persisit(QuadTree node, int index, QuadTreeDAO parent){
		Vector2 position = node.getPosition();
		QuadTreeDAO newNode = new QuadTreeDAO(position.getX(),position.getY(),node.getDepth(),node.isFilled(),node.isObstacle(),index,parent);
		newNode.save();
		QuadTree[] children = node.getChildren();
		if(node.getDepth() > 0){
			for(int i = 0; i < children.length; i++ ){
				if(children[i] != null){
					persisit(children[i],i,parent);
				}
			}
		}
		return newNode.getId();
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

	public QuadTreeDAO getParent() {
		return parent;
	}

	public void setParent(QuadTreeDAO parent) {
		this.parent = parent;
	}
}
