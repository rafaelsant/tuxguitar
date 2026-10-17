package app.tuxguitar.android.navigation;

import androidx.fragment.app.Fragment;

import app.tuxguitar.android.fragment.TGFragmentController;

public class TGNavigationFragment {

	private String tagId;
	private TGFragmentController<?> controller;
	private Fragment fragment;

	public TGNavigationFragment() {
		super();
	}

	public String getTagId() {
		return tagId;
	}

	public void setTagId(String tagId) {
		this.tagId = tagId;
	}

	public TGFragmentController<?> getController() {
		return controller;
	}

	public void setController(TGFragmentController<?> controller) {
		this.controller = controller;
	}

	/** The fragment shown for this screen while it is in the stack (not part of equals: it identifies the screen). */
	public Fragment getFragment() {
		return fragment;
	}

	public void setFragment(Fragment fragment) {
		this.fragment = fragment;
	}

	public int hashCode() {
		if( this.getTagId() != null ) {
			return (TGNavigationFragment.class.getName() + "-" + this.getTagId().hashCode()).hashCode();
		}
		if( this.getController() != null ) {
			return (TGNavigationFragment.class.getName() + "-fr-" + this.getController().hashCode()).hashCode();
		}
		return super.hashCode();
	}

	public boolean equals(Object o) {
		if( o instanceof TGNavigationFragment ) {
			return (this.hashCode() == o.hashCode());
		}
		return false;
	}
}
