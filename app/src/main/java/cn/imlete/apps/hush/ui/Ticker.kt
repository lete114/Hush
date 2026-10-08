package cn.imlete.apps.hush.ui

/**
 * The UI clock tick period and the progress-arc tween duration share the same constant, avoiding drift between the two spots.
 *
 * The two must share one source: when the tween duration is shorter than the actual tick period, a stationary gap appears between animations (still one notch at a time);
 * when it is significantly longer than the tick, the arc permanently lags the center number. When equal, the displayed value trails the target by ≤1 beat
 * (tween duration = tick period), which does not accumulate over time; an occasional `delay()` overshoot (e.g. 205ms) only causes a 5ms micro-pause,
 * imperceptible to the eye.
 *
 * Changing this value changes both the UI clock tick and the progress-arc tween duration with no other spot to touch — the tween following along automatically is the key value of this design.
 */
const val TICK_MS = 200L
