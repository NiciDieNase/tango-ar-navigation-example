package de.stetro.tango.arnavigation;

import android.app.Application;

import com.facebook.stetho.Stetho;
import com.orm.SugarContext;

/**
 * Created by felix on 06/03/17.
 */

public class ARNavigationApp extends Application {
	@Override
	public void onCreate() {
		super.onCreate();
		SugarContext.init(this);
		Stetho.initializeWithDefaults(this);
	}

	@Override
	public void onTerminate() {
		SugarContext.terminate();
		super.onTerminate();
	}
}
