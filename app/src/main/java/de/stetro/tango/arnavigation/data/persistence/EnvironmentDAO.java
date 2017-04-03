package de.stetro.tango.arnavigation.data.persistence;

import com.orm.SugarRecord;

/**
 * Created by felix on 06/03/17.
 */

public class EnvironmentDAO extends SugarRecord {

    long rootNodeId;
    double floorLevel;
    double[] mapTransform;
    String ADFUUID;
    String title;
    String description;

    public EnvironmentDAO(){};

    public EnvironmentDAO(String ADFUUID, long rootNodeId, double floorLevel){
        this.ADFUUID = ADFUUID;
        this.rootNodeId = rootNodeId;
        this.floorLevel = floorLevel;
    }

    public EnvironmentDAO(long rootNodeId, double floorLevel, String ADFUUID, String title, String description) {
        this.rootNodeId = rootNodeId;
        this.floorLevel = floorLevel;
        this.ADFUUID = ADFUUID;
        this.title = title;
        this.description = description;
    }

    public long getRootNodeId() {
        return rootNodeId;
    }

    public void setRootNodeId(long rootNodeId) {
        this.rootNodeId = rootNodeId;
    }

    public double getFloorLevel() {
        return floorLevel;
    }

    public void setFloorLevel(double floorLevel) {
        this.floorLevel = floorLevel;
    }

    public String getADFUUID() {
        return ADFUUID;
    }

    public void setADFUUID(String ADFUUID) {
        this.ADFUUID = ADFUUID;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
