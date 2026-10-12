package app.tuxguitar.android.fragment.impl;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;

import app.tuxguitar.android.R;
import app.tuxguitar.android.fragment.TGCachedFragment;
import app.tuxguitar.android.menu.controller.impl.fragment.TGBrowserMenu;

public class TGBrowserFragment extends TGCachedFragment {

	public TGBrowserFragment() {
		super(R.layout.view_browser);
	}

	@Override
	public void onPostCreate(Bundle savedInstanceState) {
		this.attachInstance();
		this.createActionBar(true, false, null);
	}

	// no top bar of its own: the action bar is shown on this screen only
	@Override
	public void onShowView() {
		this.findActivity().setLegacyActionBarVisible(true);
	}

	@Override
	public void onHideView() {
		this.findActivity().setLegacyActionBarVisible(false);
	}

	@Override
	public void onPostCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
		TGBrowserMenu.getInstance(this.findContext()).inflate(menu, menuInflater);
	}

	public void attachInstance() {
		TGBrowserFragmentController.getInstance(this.findContext()).attachInstance(this);
	}
}
