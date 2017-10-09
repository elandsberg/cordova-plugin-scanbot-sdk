package io.scanbot.sdk.plugin.cordova.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.format.DateUtils;
import android.view.animation.LinearInterpolator;
import io.scanbot.sdk.plugin.cordova.utils.ResourcesUtils;


/**
 * Shutter for camera.
 */
public class ShutterDrawable extends Drawable {

    private static final float FIRST_ROTATION_SPEED_DEGREES_PER_SEC = 143f;
    private static final float SECOND_ROTATION_SPEED_DEGREES_PER_SEC = 36f;

    private final Paint bitmapPaint;

    private final Bitmap normal;

    private final Bitmap outerCircle;
    private final Bitmap innerCircleFirst;
    private final Bitmap innerCircleSecond;
    private final Bitmap outerCircleActive;
    private final Bitmap innerCircleFirstActive;
    private final Bitmap innerCircleSecondActive;

    private Bitmap outerCircleBitmap;
    private Bitmap innerCircleFirstBitmap;
    private Bitmap innerCircleSecondBitmap;

    private boolean animated = false;
    private float animationProgressFirst = 0f;
    private float animationProgressSecond = 0f;
    private ValueAnimator firstAnimator;
    private ValueAnimator secondAnimator;

    public ShutterDrawable(final Context context) {
        bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Resources resources = context.getResources();

        normal = BitmapFactory.decodeResource(resources, ResourcesUtils.getResId("drawable", "ui_cam_shutter", context));
        outerCircle = BitmapFactory.decodeResource(resources, ResourcesUtils.getResId("drawable", "ui_snapping_button_outer", context));
        innerCircleFirst = BitmapFactory.decodeResource(resources, ResourcesUtils.getResId("drawable", "ui_snapping_button_inner66", context));
        innerCircleSecond = BitmapFactory.decodeResource(resources, ResourcesUtils.getResId("drawable", "ui_snapping_button_inner33", context));
        outerCircleActive = BitmapFactory.decodeResource(resources, ResourcesUtils.getResId("drawable", "ui_snapping_button_outer_active", context));
        innerCircleFirstActive = BitmapFactory.decodeResource(resources, ResourcesUtils.getResId("drawable", "ui_snapping_button_inner66_active", context));
        innerCircleSecondActive = BitmapFactory.decodeResource(resources, ResourcesUtils.getResId("drawable", "ui_snapping_button_inner33_active", context));

        setActive(false);
    }

    @Override
    public int getIntrinsicWidth() {
        return normal.getWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return normal.getHeight();
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect bounds = getBounds();

        final float centerX = bounds.centerX();
        final float centerY = bounds.centerY();

        if (!animated) {
            canvas.drawBitmap(
                    normal,
                    centerX - (normal.getWidth() >>> 1),
                    centerY - (normal.getHeight() >>> 1),
                    bitmapPaint
            );
            return;
        }

        canvas.drawBitmap(
                outerCircleBitmap,
                centerX - (outerCircleBitmap.getWidth() >>> 1),
                centerY - (outerCircleBitmap.getHeight() >>> 1),
                bitmapPaint
        );

        canvas.save();
        canvas.rotate(360f * animationProgressFirst, centerX, centerY);
        canvas.drawBitmap(
                innerCircleFirstBitmap,
                centerX - (innerCircleFirstBitmap.getWidth() >>> 1),
                centerY - (innerCircleFirstBitmap.getHeight() >>> 1),
                bitmapPaint
        );
        canvas.restore();

        canvas.save();
        canvas.rotate(360f * animationProgressSecond, centerX, centerY);
        canvas.drawBitmap(
                innerCircleSecondBitmap,
                centerX - (innerCircleSecondBitmap.getWidth() >>> 1),
                centerY - (innerCircleSecondBitmap.getHeight() >>> 1),
                bitmapPaint
        );
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        bitmapPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        bitmapPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    /**
     * Starts shutter animation if it is not started already.
     */
    public void startAnimation() {
        if (animated) {
            return;
        }

        animated = true;

        firstAnimator = buildAnimator(FIRST_ROTATION_SPEED_DEGREES_PER_SEC, 0f, 1f);
        firstAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                animationProgressFirst = (Float) valueAnimator.getAnimatedValue();
                invalidateSelf();
            }
        });

        secondAnimator = buildAnimator(SECOND_ROTATION_SPEED_DEGREES_PER_SEC, 1f, 0f);
        secondAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                animationProgressSecond = (Float) valueAnimator.getAnimatedValue();
                invalidateSelf();
            }
        });

        firstAnimator.start();
        secondAnimator.start();


        invalidateSelf();
    }

    private ValueAnimator buildAnimator(float speed, float... values) {
        ValueAnimator animator = ValueAnimator.ofFloat(values);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setDuration((long) (360f / speed * DateUtils.SECOND_IN_MILLIS));
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatMode(ValueAnimator.RESTART);
        return animator;
    }

    /**
     * Stops shutter animation if it is not stopped already
     */
    public void stopAnimation() {
        if (!animated) {
            return;
        }

        animated = false;

        firstAnimator.cancel();
        firstAnimator = null;

        secondAnimator.cancel();
        secondAnimator = null;

        invalidateSelf();
    }

    /**
     * Switches between shutter modes showing different drawable.
     */
    public void setActive(boolean isActive) {
        outerCircleBitmap = isActive ? outerCircleActive : outerCircle;
        innerCircleFirstBitmap = isActive ? innerCircleFirstActive : innerCircleFirst;
        innerCircleSecondBitmap = isActive ? innerCircleSecondActive : innerCircleSecond;
    }
}
