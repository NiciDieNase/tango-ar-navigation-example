package de.stetro.tango.arnavigation.rendering;

import org.rajawali3d.Object3D;
import org.rajawali3d.materials.Material;

/**
 * Created by felix on 24.03.17.
 */

public class TargetMarker extends Object3D {

    private final float h;
    private final float w;
    private final float[] color;

    public TargetMarker(float height, float baseWidth){
        super();
        this.color = new float[]{0.0f, 1.0f, 0.0f, 0.5f};
        this.h = height;
        this.w = baseWidth;
        init();
    }

    private void init() {
        float[] vertices = {
                0,0,0,
                 w/2, h,  w /2,
                 w/2, h, -w /2,
                -w/2, h, -w /2,
                -w/2, h,  w /2
        };
        int[] indices = {
                0,2,1,
                0,3,2,
                0,4,3,
                0,1,4,
                3,4,2,
                2,4,1
        };
        float[] normals = {
                h, -w/2, 0,
                0,  h, -w/2,
                h, -w/2, 0,
                0,  h, w/2,
                0,1,0,
                0,1,0
        };
        setData(vertices, normals, null, null, indices, false);
        Material material = new Material();
        material.setColor(color);
        setMaterial(material);
    }
}
