package app.tuxguitar.android.activity;

import androidx.appcompat.app.ActionBar;

/**
 * The old action bar. The theme has none now (the screens draw their own top bars and the main screen a floating
 * menu bar), so every call is ignored when there is no action bar.
 */
public class TGActivityActionBarController {

	private TGActivity activity;

	public TGActivityActionBarController(TGActivity activity) {
		this.activity = activity;
	}

	public ActionBar getActionBar() {
		return this.activity.getSupportActionBar();
	}

	public void setHomeButtonEnabled(boolean enabled) {
		if( this.getActionBar() != null ) {
			this.getActionBar().setHomeButtonEnabled(enabled);
		}
	}

	public void setDisplayHomeAsUpEnabled(boolean showHomeAsUp) {
		if( this.getActionBar() != null ) {
			this.getActionBar().setDisplayHomeAsUpEnabled(showHomeAsUp);
		}
	}

	public void setDisplayShowTitleEnabled(boolean showTitle) {
		if( this.getActionBar() != null ) {
			this.getActionBar().setDisplayShowTitleEnabled(showTitle);
		}
	}

	public void setDisplayShowHomeEnabled(boolean showHome) {
		if( this.getActionBar() != null ) {
			this.getActionBar().setDisplayShowHomeEnabled(showHome);
		}
	}

	public void setDisplayUseLogoEnabled(boolean useLogo) {
		if( this.getActionBar() != null ) {
			this.getActionBar().setDisplayUseLogoEnabled(useLogo);
		}
	}

	public void setLogo(int resId) {
		if( this.getActionBar() != null ) {
			this.getActionBar().setLogo(resId);
		}
	}

	public void setTitle(int resId) {
		if( this.getActionBar() != null ) {
			this.getActionBar().setTitle(resId);
		}
	}

	public void setTitle(CharSequence title) {
		if( this.getActionBar() != null ) {
			this.getActionBar().setTitle(title);
		}
	}
}
