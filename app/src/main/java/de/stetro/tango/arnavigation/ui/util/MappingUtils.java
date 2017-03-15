package de.stetro.tango.arnavigation.ui.util;

import android.opengl.Matrix;
import android.util.Log;

import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.experimental.TangoImageBuffer;
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

	/**
	 * Use the TangoSupport library with point cloud data to calculate the depth
	 * of the point closest to where the user touches the screen. It returns a
	 * Vector3 in openGL world space.
	 */
	public static float[] getDepthAtTouchPosition(TangoImageBuffer mCurrentImageBuffer, int mDisplayRotation, float u, float v, TangoPointCloudData pointCloud) {
//        TangoPointCloudData pointCloud = mPointCloudManager.getLatestPointCloud();
		if (pointCloud == null) {
			return null;
		}

		double rgbTimestamp;
		TangoImageBuffer imageBuffer = mCurrentImageBuffer;
		rgbTimestamp = imageBuffer.timestamp; // CPU.

		// We need to calculate the transform between the color camera at the
		// time the user clicked and the depth camera at the time the depth
		// cloud was acquired.
		TangoPoseData colorTdepthPose = TangoSupport.calculateRelativePose(
				rgbTimestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
				pointCloud.timestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH);

		float[] point;
		double[] identityTranslation = {0.0, 0.0, 0.0};
		double[] identityRotation = {0.0, 0.0, 0.0, 1.0};
		point = TangoSupport.getDepthAtPointNearestNeighbor(pointCloud,
				colorTdepthPose.translation, colorTdepthPose.rotation,
				u, v, mDisplayRotation, identityTranslation, identityRotation);
//		point = TangoSupport.getDepthAtPointBilateral(pointCloud,colorTdepthPose.translation,
//				colorTdepthPose.rotation,imageBuffer,u,v,mDisplayRotation,identityTranslation,
//				identityRotation);
		if (point == null) {
			return null;
		}

		// Get the transform from depth camera to OpenGL world at the timestamp of the cloud.
		float[] openGlPoint = colorToADFFrame(pointCloud, point);
		return openGlPoint;
	}

	public static TangoSupport.TangoMatrixTransformData getTransform(double timestamp){
		return TangoSupport.getMatrixTransformAtTime(timestamp,
				TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
				TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
				TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
				TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
				TangoSupport.ROTATION_IGNORED);
	}
}
