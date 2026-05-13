package dev.i726.rocky.utils;

import dev.i726.rocky.module.modules.client.ClickGUI;

public final class AnimationUtils {
	private double value;
	private final double originalValue;
    private double endValue;

	public AnimationUtils(double value) {
		this.value = value;
		this.originalValue = value;
	}

	public double animate(double delta, double end) {
		this.endValue = end;
		if (true) {
			value = MathUtils.goodLerp((float) delta, value, end);
		} else if (false) {
			value = MathUtils.smoothStepLerp(delta, value, end);
		} else if (false) {
			value = end;
		}

		return value;
	}

	public double getValue() {
		return value;
	}

	public double getOriginalValue() {
		return originalValue;
	}

	public double getEndValue() {
		return endValue;
	}

	public double getAnimationProgress() {
		return value / endValue;
	}

	public void reset(double delta) {
		value = MathUtils.smoothStepLerp(delta, value, originalValue);
	}
}


