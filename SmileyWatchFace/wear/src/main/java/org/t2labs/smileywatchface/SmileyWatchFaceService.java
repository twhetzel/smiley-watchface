/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.t2labs.smileywatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class SmileyWatchFaceService extends CanvasWatchFaceService {

    private static final String TAG = "SmileyWatchFaceService";

    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final float HOUR_STROKE_WIDTH = 5f;
        private static final float MINUTE_STROKE_WIDTH = 3f;
        private static final float SECOND_TICK_STROKE_WIDTH = 2f;

        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 4f;

        private static final int SHADOW_RADIUS = 6;

        private Calendar mCalendar;
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;

        private float mCenterX;
        private float mCenterY;

        private float mSecondHandLength;
        private float mMinuteHandLength;
        private float mHourHandLength;

        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private int mWatchHandColor;
        private int mWatchHandHighlightColor;
        private int mWatchHandShadowColor;


        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mSecondPaint;
        private Paint mTickAndCirclePaint;

        private Paint mBackgroundPaint;
        private Bitmap mBackgroundBitmap;
        private Bitmap mBackgroundBitmap2;
        private Bitmap mBackgroundBitmap3;
        private Bitmap mBackgroundBitmap4;
        private Bitmap mBackgroundBitmap5;
        boolean smiley2;
        private Bitmap mGrayBackgroundBitmap;

        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;

        private Paint mTextPaint;
        private float mXOffset;
        private float mYOffset;
        private float mTextSpacingHeight;
        private int mScreenTextColor = Color.RED;

        private int mTouchCommandTotal;
        private int mTouchCancelCommandTotal;
        private int mTapCommandTotal;

        private int mTouchCoordinateX;
        private int mTouchCoordinateY;

        //From Jono
        int mOrientation = 0;

        private Rect mPeekCardBounds = new Rect();

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SmileyWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());


            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);

            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.smiley1);
            mBackgroundBitmap2 = BitmapFactory.decodeResource(getResources(), R.drawable.smiley2);
            mBackgroundBitmap3 = BitmapFactory.decodeResource(getResources(), R.drawable.smiley3);
            mBackgroundBitmap4 = BitmapFactory.decodeResource(getResources(), R.drawable.smiley4);
            mBackgroundBitmap5 = BitmapFactory.decodeResource(getResources(), R.drawable.smiley5);
            smiley2 = false;

            Resources resources = SmileyWatchFaceService.this.getResources();
            mTextSpacingHeight = resources.getDimension(R.dimen.interactive_text_size);

            mTextPaint = new Paint();
            mTextPaint.setColor(mScreenTextColor);
            mTextPaint.setTypeface(BOLD_TYPEFACE);
            mTextPaint.setAntiAlias(true);

            /* Set defaults for colors */
            mWatchHandColor = Color.WHITE;
            mWatchHandHighlightColor = Color.BLUE;
            mWatchHandShadowColor = Color.WHITE;

            /* Set parameters to draw Hour hand */
            mHourPaint = new Paint();
            mHourPaint.setColor(mWatchHandColor);
            mHourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            /* Set parameters to draw Minute hand */
            mMinutePaint = new Paint();
            mMinutePaint.setColor(mWatchHandColor);
            mMinutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);
            mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            /* Set parameters to draw Second hand */
            mSecondPaint = new Paint();
            mSecondPaint.setColor(mWatchHandHighlightColor);
            mSecondPaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.ROUND);
            mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mTickAndCirclePaint = new Paint();
            mTickAndCirclePaint.setColor(mWatchHandColor);
            mTickAndCirclePaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mTickAndCirclePaint.setAntiAlias(true);
            mTickAndCirclePaint.setStyle(Paint.Style.STROKE);
            mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mTouchCommandTotal = 0;
            mTouchCancelCommandTotal = 0;
            mTapCommandTotal = 0;

            mTouchCoordinateX = 0;
            mTouchCoordinateY = 0;

            /* Extract colors from background image to improve watchface style. */
        /*    Palette.generateAsync(
                    mBackgroundBitmap,
                    new Palette.PaletteAsyncListener() {
                        @Override
                        public void onGenerated(Palette palette) {
                            if (palette != null) {
                                if (Log.isLoggable(TAG, Log.DEBUG)) {
                                    Log.d(TAG, "Palette: " + palette);
                                }

                                mWatchHandHighlightColor = palette.getVibrantColor(Color.BLUE);
                                mWatchHandColor = palette.getLightVibrantColor(Color.WHITE);
                                mWatchHandShadowColor = palette.getDarkMutedColor(Color.BLACK);
                                updateWatchHandStyle();
                            }
                        }
                    });
                    */
            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPropertiesChanged: low-bit ambient = " + mLowBitAmbient);
            }
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);
            }
            mAmbient = inAmbientMode;

            updateWatchHandStyle();

            invalidate();
        }

        private void updateWatchHandStyle() {
            if (mAmbient) {
                mHourPaint.setColor(Color.WHITE);
                mMinutePaint.setColor(Color.WHITE);
                mSecondPaint.setColor(Color.WHITE);
                mTickAndCirclePaint.setColor(Color.WHITE);

                mHourPaint.setAntiAlias(false);
                mMinutePaint.setAntiAlias(false);
                mSecondPaint.setAntiAlias(false);
                mTickAndCirclePaint.setAntiAlias(false);

                mHourPaint.clearShadowLayer();
                mMinutePaint.clearShadowLayer();
                mSecondPaint.clearShadowLayer();
                mTickAndCirclePaint.clearShadowLayer();

            } else {
                mHourPaint.setColor(mWatchHandColor);
                mMinutePaint.setColor(mWatchHandColor);
                mSecondPaint.setColor(mWatchHandHighlightColor);
                mTickAndCirclePaint.setColor(mWatchHandColor);

                mHourPaint.setAntiAlias(true);
                mMinutePaint.setAntiAlias(true);
                mSecondPaint.setAntiAlias(true);
                mTickAndCirclePaint.setAntiAlias(true);

                mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            }
        }

        /*
         * Captures tap event (and tap type) and increments correct tap type total.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Tap Command: " + tapType);
            }

            mTouchCoordinateX = x;
            mTouchCoordinateY = y;

            switch(tapType) {
                case TAP_TYPE_TOUCH:
                    mTouchCommandTotal++;
                    // Tap to make eyes rotate
                    if (smiley2) {
                        smiley2 = false;
                        Log.d(TAG, "Smiley: "+smiley2);
                    }
                    // Tap to step eyes rotating and display original image
                    else {
                        smiley2 = true;
                        Log.d(TAG, "Smiley: "+smiley2);
                    }
                    Log.d(TAG, "TAP_TYPE_TOUCH detected");
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    mTouchCancelCommandTotal++;
                    Log.d(TAG, "TAP_TYPE_TOUCH_CANCEL detected");
                    break;
                case TAP_TYPE_TAP:
                    //smiley2 = false;
                    mTapCommandTotal++;
                    Log.d(TAG, "TAP_TYPE_TAP detected");
                    break;
            }
            invalidate();
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "onDraw");
            }
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (float) (mCenterX * 0.875);
            mMinuteHandLength = (float) (mCenterX * 0.75);
            mHourHandLength = (float) (mCenterX * 0.5);


            /* Scale loaded background image (more efficient) if surface dimensions change. */
            float scale = ((float) width) / (float) mBackgroundBitmap.getWidth();
            Log.d(TAG, "(PRE) onSurfaceChanged: Scale: " + scale);


            Log.d(TAG, "(PRE) onSurfaceChanged: BG: "+ mBackgroundBitmap.getWidth() + ", BG2: " +
                    mBackgroundBitmap2.getWidth());

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * scale),
                    (int) (mBackgroundBitmap.getHeight() * scale), true);

            mBackgroundBitmap2 = Bitmap.createScaledBitmap(mBackgroundBitmap2,
                    (int) (mBackgroundBitmap2.getWidth() * scale),
                    (int) (mBackgroundBitmap2.getHeight() * scale), true);

            mBackgroundBitmap3 = Bitmap.createScaledBitmap(mBackgroundBitmap3,
                    (int) (mBackgroundBitmap3.getWidth() * scale),
                    (int) (mBackgroundBitmap3.getHeight() * scale), true);

            mBackgroundBitmap4 = Bitmap.createScaledBitmap(mBackgroundBitmap4,
                    (int) (mBackgroundBitmap4.getWidth() * scale),
                    (int) (mBackgroundBitmap4.getHeight() * scale), true);

            mBackgroundBitmap5 = Bitmap.createScaledBitmap(mBackgroundBitmap5,
                    (int) (mBackgroundBitmap5.getWidth() * scale),
                    (int) (mBackgroundBitmap5.getHeight() * scale), true);

            Log.d(TAG, "onSurfaceChanged: BG: "+ mBackgroundBitmap.getWidth() + ", BG2: " +
                    mBackgroundBitmap2.getWidth());

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don't want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap();
            }
        }

        private void initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                    mBackgroundBitmap.getWidth(),
                    mBackgroundBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mGrayBackgroundBitmap);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, grayPaint);
        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "onDraw");
            }
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            // Reflects taps
            canvas.drawText(
                    "TAP: " + String.valueOf(mTapCommandTotal),
                    mXOffset,
                    mYOffset,
                    mTextPaint);

            canvas.drawText(
                    "CANCEL: " + String.valueOf(mTouchCancelCommandTotal),
                    mXOffset,
                    mYOffset + mTextSpacingHeight,
                    mTextPaint);


            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0, 0, mBackgroundPaint);
            } else if (smiley2) {
                Log.d(TAG, "Value of smiley(isInteractive): "+ smiley2);
                /* Loop through all images to show eyes circling */
                //changeBackgroundImage(canvas);

                // New approach from Jono
                // Draw images in order of eyes rotating in a circle
                if (mOrientation < 50) {
                    Log.d(TAG, "Orientation: "+mOrientation);
                    canvas.drawBitmap(mBackgroundBitmap2, 0, 0, mBackgroundPaint);
                    mOrientation+=5;
                    Log.d(TAG, "Orientation: "+mOrientation);
                }
                else if (mOrientation >= 50 && mOrientation < 100) {
                    Log.d(TAG, "Orientation: "+mOrientation);
                    canvas.drawBitmap(mBackgroundBitmap3, 0, 0, mBackgroundPaint);
                    mOrientation+=5;
                }
                else if (mOrientation >= 100 && mOrientation < 150) {
                    Log.d(TAG, "Orientation: "+mOrientation);
                    canvas.drawBitmap(mBackgroundBitmap4, 0, 0, mBackgroundPaint);
                    mOrientation+=5;
                }
                else if (mOrientation >= 150 && mOrientation < 200) {
                    Log.d(TAG, "Orientation: "+mOrientation);
                    canvas.drawBitmap(mBackgroundBitmap5, 0, 0, mBackgroundPaint);
                    mOrientation+=5;
                }
                else {
                    // Reset
                    smiley2 = false;
                    Log.d(TAG, "Orientation: "+mOrientation);
                    canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
                }

                int delayLength;
                switch(mOrientation) {
                    case 0:
                        delayLength = 50;
                        Log.d(TAG, "Delay:"+delayLength);
                        break;
                    case 50:
                        delayLength = 50;
                        Log.d(TAG, "Delay:"+delayLength);
                        break;
                    case 100:
                        delayLength = 50;
                        Log.d(TAG, "Delay:"+delayLength);
                        break;
                    case 150:
                        delayLength = 50;
                        Log.d(TAG, "Delay: "+delayLength);
                        break;
                    default:
                        delayLength = 5;
                        break;
                }
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "Runnable working...");
                        invalidate();
                    }
                }, delayLength);

            } else {
                Log.d(TAG, "Reset");
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
                mOrientation = 0;
            }

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            final float seconds =
                    (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation = seconds * 6f;

            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;

            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;


            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save();

            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - mHourHandLength,
                    mHourPaint);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - mMinuteHandLength,
                    mMinutePaint);

            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawLine(
                        mCenterX,
                        mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                        mCenterX,
                        mCenterY - mSecondHandLength,
                        mSecondPaint);

            }
            canvas.drawCircle(
                    mCenterX,
                    mCenterY,
                    CENTER_GAP_AND_CIRCLE_RADIUS,
                    mTickAndCirclePaint);

            /* Restore the canvas' original orientation. */
            canvas.restore();

            /* Draw rectangle behind peek card in ambient mode to improve readability. */
            if (mAmbient) {
                canvas.drawRect(mPeekCardBounds, mBackgroundPaint);
            }

            /* Draw every frame as long as we're visible and in interactive mode. */
            if ((isVisible()) && (!mAmbient)) {
                invalidate();
            }

        }

        public void changeBackgroundImage(Canvas canvas) {
            Bitmap [] myImages = {mBackgroundBitmap, mBackgroundBitmap2, mBackgroundBitmap3,
            mBackgroundBitmap4, mBackgroundBitmap5};

            // Randomly draw images
//            Random ran = new Random();
//            int x = ran.nextInt(5);
//            Log.d(TAG, "x = " + x);
//            canvas.drawBitmap(myImages[x], 0, 0, mBackgroundPaint);

            // Draw images in order of eyes rotating in a circle
            for (int i = 0; i < myImages.length; i++) {
                Log.d(TAG, "Int:"+i);
                canvas.drawBitmap(myImages[i], 0, 0, mBackgroundPaint);
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));
            }
            super.onApplyWindowInsets(insets);

            /** Loads offsets / text size based on device type (square vs. round). */
            Resources resources = SmileyWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(
                    isRound ? R.dimen.interactive_x_offset_round : R.dimen.interactive_x_offset);
            mYOffset = resources.getDimension(
                    isRound ? R.dimen.interactive_y_offset_round : R.dimen.interactive_y_offset);

            float textSize = resources.getDimension(
                    isRound ? R.dimen.interactive_text_size_round : R.dimen.interactive_text_size);

            mTextPaint.setTextSize(textSize);
        }

        @Override
        public void onPeekCardPositionUpdate(Rect rect) {
            super.onPeekCardPositionUpdate(rect);
            mPeekCardBounds.set(rect);
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SmileyWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SmileyWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

    }
}
