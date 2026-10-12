package app.tuxguitar.android.view.tuner;

/**
 * Fundamental frequency detection with the YIN algorithm (de Cheveigné and Kawahara, 2002).
 * Plain Java with no Android dependencies, so it can be tested on its own.
 */
public class TGPitchDetector {

	/** Result for a buffer with no clear pitch (silence or noise). */
	public static final float NO_PITCH = -1f;

	private static final float THRESHOLD = 0.15f;

	private final int sampleRate;
	private final int tauMin;
	private final int tauMax;
	private final float minRms;
	private float[] difference;
	private float clarity;

	/**
	 * @param minFrequency lowest frequency to look for (the lag range, and so the cost, grows as it goes down)
	 * @param maxFrequency highest frequency to look for
	 * @param minRms buffers quieter than this (samples in -1..1) are treated as silence
	 */
	public TGPitchDetector(int sampleRate, float minFrequency, float maxFrequency, float minRms) {
		this.sampleRate = sampleRate;
		this.tauMin = Math.max(2, (int) Math.floor(sampleRate / maxFrequency));
		this.tauMax = (int) Math.ceil(sampleRate / minFrequency);
		this.minRms = minRms;
	}

	/** Buffer size needed to look down to the minimum frequency: the lag range plus a window of the same size. */
	public int getMinBufferSize() {
		return this.tauMax * 2;
	}

	/** How clear the last detected pitch was, from 0 to 1 (1 - the normalized difference at the chosen lag). */
	public float getClarity() {
		return this.clarity;
	}

	/** Returns the fundamental frequency in Hz of the first [length] samples, or [NO_PITCH]. */
	public float detect(float[] samples, int length) {
		this.clarity = 0f;
		if( length < this.getMinBufferSize() || rms(samples, length) < this.minRms ) {
			return NO_PITCH;
		}
		int window = length - this.tauMax;
		if( this.difference == null || this.difference.length < this.tauMax + 1 ) {
			this.difference = new float[this.tauMax + 1];
		}
		float[] d = this.difference;

		// difference function
		for(int tau = 1; tau <= this.tauMax; tau ++) {
			float sum = 0f;
			for(int i = 0; i < window; i ++) {
				float delta = samples[i] - samples[i + tau];
				sum += delta * delta;
			}
			d[tau] = sum;
		}

		// cumulative mean normalized difference
		d[0] = 1f;
		float runningSum = 0f;
		for(int tau = 1; tau <= this.tauMax; tau ++) {
			runningSum += d[tau];
			d[tau] = (runningSum > 0f ? d[tau] * tau / runningSum : 1f);
		}

		// first dip under the threshold, followed down to its minimum
		int tau = -1;
		for(int t = this.tauMin; t < this.tauMax; t ++) {
			if( d[t] < THRESHOLD ) {
				while( t + 1 < this.tauMax && d[t + 1] < d[t] ) {
					t ++;
				}
				tau = t;
				break;
			}
		}
		if( tau < 0 ) {
			return NO_PITCH;
		}
		this.clarity = Math.max(0f, 1f - d[tau]);

		// parabolic interpolation around the minimum for sub-sample precision
		float betterTau = tau;
		if( tau > 1 && tau < this.tauMax ) {
			float s0 = d[tau - 1];
			float s1 = d[tau];
			float s2 = d[tau + 1];
			float divisor = 2f * (2f * s1 - s2 - s0);
			if( divisor != 0f ) {
				betterTau = tau + (s2 - s0) / divisor;
			}
		}
		return (this.sampleRate / betterTau);
	}

	public static float rms(float[] samples, int length) {
		double sum = 0;
		for(int i = 0; i < length; i ++) {
			sum += samples[i] * samples[i];
		}
		return (float) Math.sqrt(sum / Math.max(1, length));
	}
}
