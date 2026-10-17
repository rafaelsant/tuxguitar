package app.tuxguitar.android.navigation;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.action.TGActionException;
import app.tuxguitar.android.R;
import app.tuxguitar.android.action.impl.gui.TGOpenFragmentAction;
import app.tuxguitar.android.activity.TGActivity;
import app.tuxguitar.android.application.TGDiagnostics;
import app.tuxguitar.android.fragment.TGFragmentController;
import app.tuxguitar.editor.action.TGActionProcessor;
import app.tuxguitar.event.TGEventListener;
import app.tuxguitar.event.TGEventManager;
import app.tuxguitar.util.TGContext;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class TGNavigationManager {

	private TGActivity activity;
	private List<TGNavigationFragment> navigationFragments;

	public TGNavigationManager(TGActivity activity) {
		this.activity = activity;
		this.navigationFragments = new ArrayList<TGNavigationFragment>();
	}

	public void initialize() {
		this.navigationFragments.clear();
	}

	public void processLoadFragment(TGFragmentController<?> controller, String tagId) {
		TGNavigationFragment tgNavigationFragment = new TGNavigationFragment();
		tgNavigationFragment.setController(controller);
		tgNavigationFragment.setTagId(tagId);

		this.processLoadFragment(tgNavigationFragment);
	}

	public void processLoadFragment(TGNavigationFragment nf) {
		final String name = nf.getController().getClass().getSimpleName();
		TGDiagnostics.log("load fragment " + name + " (history " + this.navigationFragments.size() + ")");
		// Screens are stacked: a new screen is added on top and the one below stays in the window, hidden; going back
		// only removes the screens above. Replacing the main screen and attaching it again on the way back left the
		// window frozen on the phone.
		FragmentTransaction transaction = this.activity.getSupportFragmentManager().beginTransaction();
		TGNavigationFragment backFrom = null;
		int index = this.navigationFragments.indexOf(nf);
		if( index >= 0 ) {
			TGNavigationFragment existing = this.navigationFragments.get(index);
			while( this.navigationFragments.size() > index + 1 ) {
				TGNavigationFragment removed = this.navigationFragments.remove(this.navigationFragments.size() - 1);
				if( removed.getFragment() != null ) {
					transaction.remove(removed.getFragment());
				}
				backFrom = removed;
			}
			Fragment fragment = existing.getFragment();
			if( fragment == null || !fragment.isAdded() ) {
				fragment = nf.getController().getFragment();
				existing.setFragment(fragment);
				transaction.add(R.id.content_frame, fragment);
			}
			transaction.show(fragment);
			nf = existing;
		} else {
			TGNavigationFragment current = this.getCurrentFragment();
			if( current != null && current.getFragment() != null ) {
				transaction.hide(current.getFragment());
			}
			Fragment fragment = nf.getController().getFragment();
			nf.setFragment(fragment);
			// shown explicitly: a reused fragment may still be marked hidden from an earlier stay in the stack
			transaction.add(R.id.content_frame, fragment);
			transaction.show(fragment);
			this.navigationFragments.add(nf);
		}
		transaction.commitAllowingStateLoss();

		TGDiagnostics.log("screen stack: " + this.describeStack());
		this.fireNavigationEvent(nf, backFrom);
	}

	private String describeStack() {
		StringBuilder text = new StringBuilder();
		for(TGNavigationFragment nf : this.navigationFragments) {
			if( text.length() > 0 ) {
				text.append(" > ");
			}
			text.append(nf.getController().getClass().getSimpleName());
		}
		return text.toString();
	}

	public TGNavigationFragment getCurrentFragment() {
		if( this.navigationFragments.size() > 0 ) {
			return this.navigationFragments.get(this.navigationFragments.size() - 1);
		}
		return null;
	}

	public TGNavigationFragment getPreviousFragment() {
		if( this.navigationFragments.size() > 1 ) {
			return this.navigationFragments.get(this.navigationFragments.size() - 2);
		}
		return null;
	}

	public void removeLastFragment() {
		if( this.navigationFragments.size() > 0 ) {
			this.navigationFragments.remove(this.navigationFragments.size() - 1);
		}
	}

	public boolean hasPreviousFragment() {
		return (this.getPreviousFragment() != null);
	}

	public TGContext findContext() {
		return this.activity.findContext();
	}

	public boolean callOpenPreviousFragment() {
		if( this.hasPreviousFragment() ) {
			this.callOpenFragment(this.getPreviousFragment());

			return true;
		}
		return false;
	}

	public void callOpenFragment(TGNavigationFragment nf) {
		this.callOpenFragment(nf.getController(), nf.getTagId());
	}

	public void callOpenFragment(TGFragmentController<?> controller) {
		this.callOpenFragment(controller, null);
	}

	public void callOpenFragment(TGFragmentController<?> controller, String tagId) {
		TGActionProcessor tgActionProcessor = new TGActionProcessor(findContext(), TGOpenFragmentAction.NAME);
		tgActionProcessor.setAttribute(TGOpenFragmentAction.ATTRIBUTE_ACTIVITY, this.activity);
		tgActionProcessor.setAttribute(TGOpenFragmentAction.ATTRIBUTE_CONTROLLER, controller);
		tgActionProcessor.setAttribute(TGOpenFragmentAction.ATTRIBUTE_TAG_ID, tagId);
		tgActionProcessor.processOnNewThread();
	}

	public void addNavigationListener(TGEventListener listener){
		TGEventManager.getInstance(findContext()).addListener(TGNavigationEvent.EVENT_TYPE, listener);
	}

	public void removeNavigationListener(TGEventListener listener){
		TGEventManager.getInstance(findContext()).removeListener(TGNavigationEvent.EVENT_TYPE, listener);
	}

	public void fireNavigationEvent(TGNavigationFragment nf, TGNavigationFragment backFrom) throws TGActionException{
		TGEventManager.getInstance(findContext()).fireEvent(new TGNavigationEvent(nf, backFrom));
	}
}
