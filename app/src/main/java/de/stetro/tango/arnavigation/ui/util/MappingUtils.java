package de.stetro.tango.arnavigation.ui.util;

import android.opengl.Matrix;
import android.util.Log;

import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangosupport.TangoSupport;

/**
 * Created by felix on 13/03/17.
 */

public class MappingUtils {

	private static final String TAG = MappingUtils.class.getSimpleName();

	public static float[] colorToADFFrame(TangoPointCloudData pointCloud, float[] point) {
		TangoSupport.TangoMatrixTransformData transform =
				TangoSupport.getMatrixTransformAtTime(pointCloud.timestamp,
						TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
						TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
						TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
						TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
						TangoSupport.ROTATION_IGNORED);
		return frameTransform(pointCloud.timestamp, point, transform);
	}

	public static float[] depthToADFFrame(TangoPointCloudData pointCloud, float[] point) {
		TangoSupport.TangoMatrixTransformData transform =
				TangoSupport.getMatrixTransformAtTime(pointCloud.timestamp,
						TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
						TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
						TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
						TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
						TangoSupport.ROTATION_IGNORED);
		return frameTransform(pointCloud.timestamp, point, transform);
	}

	public static  float[] frameTransform(double timestamp, float[] point, TangoSupport.TangoMatrixTransformData transform) {
		if (transform.statusCode == TangoPoseData.POSE_VALID) {
			float[] depthPoint = new float[]{point[0], point[1], point[2], 1};
			float[] openGlPoint = new float[4];
			Matrix.multiplyMV(openGlPoint, 0, transform.matrix, 0, depthPoint, 0);
			return openGlPoint;
		} else {
			Log.w(TAG, "Could not get depth camera transform at time " + timestamp);
		}
		return null;
	}
}
