package app.tuxguitar.android.application;

import android.content.Context;

import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

public class TGApplication extends MultiDexApplication {

	public TGApplication() {
		super();
	}

	@Override
	public void onCreate() {
		super.onCreate();

		// freeze and crash reports, shared from Settings
		TGDiagnostics.start(this);
	}

	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);

		MultiDex.install(this);
	}
}
