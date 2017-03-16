package de.stetro.tango.arnavigation.data.persistence;

import com.orm.SugarRecord;

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
}
