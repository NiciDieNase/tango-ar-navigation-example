package de.stetro.tango.arnavigation.data.persistence;

import com.orm.SugarRecord;

/**
 * Created by felix on 06/03/17.
 */

public class EnvironmentDAO extends SugarRecord {

    QuadTreeDAO rootNode;
    double floorLevel;
    String ADFUUID;
}
