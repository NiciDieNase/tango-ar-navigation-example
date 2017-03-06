package de.stetro.tango.arnavigation.data.persistence;

import com.orm.SugarRecord;

/**
 * Created by felix on 06/03/17.
 */

public class EnvironmentDAO extends SugarRecord {

    long rootNodeId;
    double floorLevel;
    String ADFUUID;

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
}
