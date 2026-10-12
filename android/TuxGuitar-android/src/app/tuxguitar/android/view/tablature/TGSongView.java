package app.tuxguitar.android.view.tablature;

import app.tuxguitar.android.application.TGApplicationUtil;
import app.tuxguitar.android.graphics.TGPainterImpl;
import app.tuxguitar.android.transport.TGTransport;
import app.tuxguitar.android.transport.TGTransportCache;
import app.tuxguitar.editor.TGEditorManager;
import app.tuxguitar.ui.resource.UIPainter;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.graphics.control.TGBeatImpl;
import app.tuxguitar.graphics.control.TGMeasureImpl;
import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.ui.resource.UIColorModel;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGException;
import app.tuxguitar.util.error.TGErrorManager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class TGSongView extends View {

	private static final float HEADER_HEIGHT_DP = 72;
	private static final float HEADER_HEIGHT_NO_ARTIST_DP = 50;
	private static final float BOTTOM_SPACE_DP = 88;

	private TGContext context;
	private TGSongViewController controller;
	private TGSongViewGestureDetector gestureDetector;

	private Bitmap bufferedBitmap;
	private boolean painting;

	public TGSongView(Context context) {
		super(context);
	}

	public TGSongView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public TGSongView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		this.context = TGApplicationUtil.findContext(this);
		this.controller = TGSongViewController.getInstance(this.context);
		this.controller.setSongView(this);
		this.controller.getLayout().loadStyles(this.getDefaultScale());
		this.controller.updateTablature();
		this.gestureDetector = new TGSongViewGestureDetector(getContext(), this);
	}

	public float getDefaultScale() {
		return this.getResources().getDisplayMetrics().density;
	}

	public float getMinimumScale() {
		return (this.getDefaultScale() / 2f);
	}

	public float getMaximumScale() {
		return (this.getDefaultScale() * 2f);
	}

	public void redraw() {
		this.setPainting(true);
		this.postInvalidate();
	}

	public void paintBuffer(Canvas canvas) {
		try {
			UIRectangle area = createClientArea(canvas);

			UIPainter painter = createBufferedPainter(area);

			this.paintArea(painter, area);

			if (this.controller.getScalePreview() != TGSongViewController.EMPTY_SCALE) {
				float currentSale = (1 / (this.controller.getLayout().getScale()) * this.controller.getScalePreview());
				((TGPainterImpl) painter).getCanvas().scale(currentSale, currentSale);
			}

			this.paintTablature(painter, area);

			painter.dispose();
		} catch (Throwable throwable) {
			TGErrorManager.getInstance(this.context).handleError(throwable);
		}
	}

	public void paintArea(UIPainter painter, UIRectangle area) {
		painter.setBackground(this.controller.getResourceFactory().createColor(this.controller.getStyles().getBackgroundColor()));
		painter.initPath(UIPainter.PATH_FILL);
		painter.addRectangle(area.getX(), area.getY(), area.getWidth(), area.getHeight());
		painter.closePath();
	}

	public void paintTablature(UIPainter painter, UIRectangle area) {
		if (this.controller.getSong() != null) {
			float headerHeight = this.getHeaderHeight();
			this.controller.getLayoutPainter().paint(painter, area, -this.getPaintableScrollX(), -this.getPaintableScrollY() + headerHeight);
			this.paintHeader(painter, headerHeight);
			this.controller.getCaret().paintCaret(this.controller.getLayout(), painter);

			this.controller.updateScroll(area, headerHeight + this.getBottomSpace());

			if (MidiPlayer.getInstance(this.context).isRunning()) {
				this.paintTablaturePlayMode(painter, area);
			}
			// Si no estoy reproduciendo y hay cambios
			// muevo el scroll al compas seleccionado
			else if (this.controller.getCaret().hasChanges()) {
				// Mover el scroll puede necesitar redibujar
				// por eso es importante desmarcar los cambios antes de hacer el
				// moveScrollTo
				this.controller.getCaret().setChanges(false);

				this.moveScrollTo(this.controller.getCaret().getMeasure(), area);
			}
		}
	}

	/** Room above the score for the song title and artist, which scroll with it; none for a song without a name. */
	public float getHeaderHeight() {
		TGSong song = this.controller.getSong();
		if( song == null || song.getName() == null || song.getName().trim().isEmpty() ) {
			return 0;
		}
		boolean artist = (song.getArtist() != null && !song.getArtist().trim().isEmpty());
		return this.getDefaultScale() * (artist ? HEADER_HEIGHT_DP : HEADER_HEIGHT_NO_ARTIST_DP);
	}

	/** Room below the score, so the floating menu bar never covers its last line. */
	public float getBottomSpace() {
		return this.getDefaultScale() * BOTTOM_SPACE_DP;
	}

	private void paintHeader(UIPainter painter, float headerHeight) {
		if( headerHeight <= 0 || !(painter instanceof TGPainterImpl) ) {
			return;
		}
		TGSong song = this.controller.getSong();
		Canvas canvas = ((TGPainterImpl) painter).getCanvas();
		float density = this.getDefaultScale();
		float top = -this.getPaintableScrollY();
		if( top + headerHeight < 0 ) {
			return;
		}
		UIColorModel color = this.controller.getStyles().getForegroundColor();
		int rgb = Color.rgb(color.getRed(), color.getGreen(), color.getBlue());

		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setColor(rgb);
		paint.setTypeface(Typeface.DEFAULT_BOLD);
		paint.setTextSize(22 * density);
		canvas.drawText(ellipsize(song.getName().trim(), paint, canvas), 16 * density, top + 34 * density, paint);

		if( song.getArtist() != null && !song.getArtist().trim().isEmpty() ) {
			paint.setTypeface(Typeface.DEFAULT);
			paint.setTextSize(15 * density);
			paint.setAlpha(170);
			canvas.drawText(ellipsize(song.getArtist().trim(), paint, canvas), 16 * density, top + 58 * density, paint);
		}
	}

	private String ellipsize(String text, Paint paint, Canvas canvas) {
		float width = (this.getWidth() - 32 * this.getDefaultScale());
		return TextUtils.ellipsize(text, new TextPaint(paint), Math.max(width, 0), TextUtils.TruncateAt.END).toString();
	}

	public void paintTablaturePlayMode(UIPainter painter, UIRectangle area) {
		TGTransportCache transportCache = TGTransport.getInstance(this.context).getCache();
		TGMeasureImpl measure = transportCache.getPlayMeasure();
		TGBeatImpl beat = transportCache.getPlayBeat();
		if (measure != null && measure.hasTrack(this.controller.getCaret().getTrack().getNumber())) {
			this.moveScrollTo(measure, area);

			if(!measure.isOutOfBounds() ) {
				this.controller.getLayout().paintPlayMode(painter, measure, beat);
			}
		}
	}

	public boolean moveScrollTo(TGMeasureImpl measure, UIRectangle area) {
		boolean success = false;
		if (measure != null && measure.getTs() != null) {
			int scrollX = getPaintableScrollX();
			int scrollY = getPaintableScrollY();

			float mX = measure.getPosX();
			float mY = measure.getPosY();
			float mWidth = measure.getWidth(this.controller.getLayout());
			float mHeight = measure.getTs().getSize();
			float marginWidth = this.controller.getLayout().getFirstMeasureSpacing();
			float marginHeight = this.controller.getLayout().getFirstTrackSpacing();

			// Solo se ajusta si es necesario
			// si el largo del compas es mayor al de la pantalla. nunca se puede
			// ajustar a la medida.
			if (mX < 0 || ((mX + mWidth) > area.getWidth() && (area.getWidth() >= mWidth + marginWidth || mX > marginWidth))) {
				this.controller.getScroll().getX().setValue((scrollX + mX) - marginWidth);
				success = true;
			}

			// Solo se ajusta si es necesario
			// si el alto del compas es mayor al de la pantalla. nunca se puede
			// ajustar a la medida.
			// the bottom of the view is covered by the floating menu bar: follow the score above it
			float visibleHeight = area.getHeight() - this.getBottomSpace();
			if (mY < 0 || ((mY + mHeight) > visibleHeight && (visibleHeight >= mHeight + marginHeight || mY > marginHeight))) {
				this.controller.getScroll().getY().setValue((scrollY + mY) - marginHeight);
				success = true;
			}

			if (success) {
				redraw();
			}
		}
		return success;
	}

	public void onDraw(Canvas canvas) {
		try {
			if(!this.controller.isDisposed()) {
				TGEditorManager editor = TGEditorManager.getInstance(this.context);
				if (editor.tryLock()) {
					try {
						this.setPainting(true);

						this.paintBuffer(canvas);

						this.setPainting(false);
					} finally {
						editor.unlock();
					}
				} else {
					// try later
					this.postInvalidate();
				}
			}

			if (this.bufferedBitmap != null) {
				canvas.drawBitmap(this.bufferedBitmap, 0, 0, null);
			}
		} catch (Throwable throwable) {
			this.handleError(throwable);
		}
	}

	public boolean onTouchEvent(MotionEvent event) {
		this.gestureDetector.processTouchEvent(event);
		this.redraw();

		return true;
	}

	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);

		this.controller.getCaret().setChanges(true);
		this.controller.resetScroll();
		this.redraw();
	}

	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();

		this.recycleBuffer();
		this.controller.getLayoutPainter().dispose();
	}

	public UIPainter createPainter(Canvas canvas) {
		return new TGPainterImpl(canvas);
	}

	public UIPainter createBufferedPainter(UIRectangle area) {
		if( this.bufferedBitmap == null || this.bufferedBitmap.getWidth() != area.getWidth() || this.bufferedBitmap.getHeight() != area.getHeight() ) {
			this.recycleBuffer();
			this.bufferedBitmap = Bitmap.createBitmap(Math.round(area.getWidth()), Math.round(area.getHeight()), Bitmap.Config.ARGB_8888);
		}
		return createPainter(new Canvas(this.bufferedBitmap));
	}

	public void recycleBuffer() {
		if( this.bufferedBitmap != null && !this.bufferedBitmap.isRecycled() ) {
			this.bufferedBitmap.recycle();
			this.bufferedBitmap = null;
		}
	}

	public UIRectangle createClientArea(Canvas canvas) {
		Rect rect = canvas.getClipBounds();
		return new UIRectangle(rect.left, rect.top, rect.right, rect.bottom);
	}

	public void handleError(Throwable throwable) {
		TGErrorManager.getInstance(this.context).handleError(new TGException(throwable));
	}

	public int getPaintableScrollX() {
		if (this.controller.getScroll().getX().isEnabled()) {
			return Math.round(this.controller.getScroll().getX().getValue());
		}
		return 0;
	}

	public int getPaintableScrollY() {
		if (this.controller.getScroll().getY().isEnabled()) {
			return Math.round(this.controller.getScroll().getY().getValue());
		}
		return 0;
	}

	public TGSongViewController getController() {
		return controller;
	}

	public boolean isPainting() {
		return this.painting;
	}

	public void setPainting(boolean painting) {
		this.painting = painting;
	}
}
