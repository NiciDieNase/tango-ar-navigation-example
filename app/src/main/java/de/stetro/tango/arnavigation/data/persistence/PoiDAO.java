package de.stetro.tango.arnavigation.data.persistence;

import com.orm.SugarRecord;

import org.rajawali3d.math.vector.Vector3;

/**
 * Created by felix on 16/03/17.
 */

public class PoiDAO extends SugarRecord {

	long environmentID;
	String name;
	String description;
	double x;
	double y;
	double z;

	public PoiDAO() {
	}

	public PoiDAO(long environmentID, String name, String description, double x, double y, double z) {
		this.environmentID = environmentID;
		this.name = name;
		this.description = description;
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	public double getZ() {
		return z;
	}

	public Vector3 getPosition(){
		return new Vector3(x,z,-y);
	}

	public long getEnvironmentID() {
		return environmentID;
	}

	public void setEnvironmentID(long environmentID) {
		this.environmentID = environmentID;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setX(double x) {
		this.x = x;
	}

	public void setY(double y) {
		this.y = y;
	}

	public void setZ(double z) {
		this.z = z;
	}
}
