package com.jaredrummler.fastscrollrecyclerview;

import ohos.agp.animation.AnimatorGroup;
import ohos.agp.animation.AnimatorValue;
import ohos.agp.components.Attr;
import ohos.agp.components.AttrHelper;
import ohos.agp.components.AttrSet;
import ohos.agp.render.Canvas;
import ohos.agp.render.Paint;
import ohos.agp.render.Path;
import ohos.agp.utils.Color;
import ohos.agp.utils.Point;
import ohos.agp.utils.Rect;
import ohos.global.resource.NotExistException;
import ohos.global.resource.ResourceManager;
import ohos.global.resource.WrongTypeException;
import ohos.multimodalinput.event.TouchEvent;

import java.io.IOException;
import java.util.Optional;

import static com.jaredrummler.fastscrollrecyclerview.Utilities.getAnimatedColor;

public class FastScrollBar {

    private final static int MAX_TRACK_ALPHA = 30;
    private final static int SCROLL_BAR_VIS_DURATION = 150;

    private final Rect invalidateRect = new Rect();
    private final Rect tmpRect = new Rect();

    /*package*/ final Point thumbOffset = new Point(-1, -1);
    private final Path thumbPath = new Path();

    /*package*/ FastScrollRecyclerView recyclerView;
    private FastScrollPopup fastScrollPopup;

    private AnimatorGroup scrollbarAnimator;

    private Color thumbInactiveColor;
    private Color thumbActiveColor;
    private Color trackColor;

    /*package*/ Paint thumbPaint;
    private int thumbMinWidth;
    private int thumbMaxWidth;
    /*package*/ int thumbWidth;
    /*package*/ int thumbHeight;
    private int thumbCurvature;

    private Paint trackPaint;
    private int trackWidth;
    private float lastTouchY;

    // The inset is the buffer around which a point will still register as a click on the scrollbar
    private int touchInset;

    private boolean isDragging;
    private boolean isThumbDetached;
    private boolean canThumbDetach;
    private boolean ignoreDragGesture;
    private boolean showThumbCurvature;

    // This is the offset from the top of the scrollbar when the user first starts touching.
    // To prevent jumping, this offset is applied as the user scrolls.
    private int touchOffset;

    public FastScrollBar(FastScrollRecyclerView rv, AttrSet attrs) {

        Optional<Attr> attr;
        if (attrs != null) {
            attr = attrs.getAttr(Attribute.FAST_SCROLL_THUMB_CURVATURE_ENABLED);
            showThumbCurvature = attr.map(Attr::getBoolValue).orElse(false);

            attr = attrs.getAttr(Attribute.FAST_SCROLL_THUMB_INACTIVE_COLOR);
            thumbInactiveColor = attr.map(Attr::getColorValue).orElse(new Color(ResourceTable.Color_fastscroll_thumb_inactive_color));

            attr = attrs.getAttr(Attribute.FAST_SCROLL_THUMB_ACTIVE_COLOR);
            thumbActiveColor = attr.map(Attr::getColorValue).orElse(new Color(ResourceTable.Color_fastscroll_thumb_active_color));

            attr = attrs.getAttr(Attribute.FAST_SCROLL_TRACK_COLOR);
            trackColor = attr.map(Attr::getColorValue).orElse(Color.BLACK);
        }

        recyclerView = rv;
        fastScrollPopup = new FastScrollPopup(rv, attrs);
        trackPaint = new Paint();
        trackPaint.setColor(trackColor);
        trackPaint.setAlpha(MAX_TRACK_ALPHA);
        thumbPaint = new Paint();
        thumbPaint.setAntiAlias(true);
        thumbPaint.setColor(thumbInactiveColor);
        thumbPaint.setStyle(Paint.Style.FILL_STYLE);

        ResourceManager res = rv.getResourceManager();

        try {
            thumbWidth = thumbMinWidth = AttrHelper.vp2px(res.getElement(ResourceTable.Float_fastscroll_thumb_min_width).getFloat(), rv.getContext());
            thumbMaxWidth = AttrHelper.vp2px(res.getElement(ResourceTable.Float_fastscroll_thumb_max_width).getFloat(), rv.getContext());
            thumbHeight = AttrHelper.vp2px(res.getElement(ResourceTable.Float_fastscroll_thumb_height).getFloat(), rv.getContext());
            thumbCurvature = showThumbCurvature ? thumbMaxWidth - thumbMinWidth : 0;
            touchInset = AttrHelper.vp2px(res.getElement(ResourceTable.Float_fastscroll_thumb_touch_inset).getFloat(), rv.getContext());
        } catch (IOException | NotExistException | WrongTypeException e) {
            e.printStackTrace();
        }

        if (rv.isFastScrollAlwaysEnabled()) {
            animateScrollbar(true);
        }
    }

    public void setDetachThumbOnFastScroll() {
        canThumbDetach = true;
    }

    public void reattachThumbToScroll() {
        isThumbDetached = false;
    }

    public void setThumbOffset(int x, int y) {
        if (thumbOffset.getPointX() == x && thumbOffset.getPointY() == y) {
            return;
        }
        invalidateRect
                .set(thumbOffset.getPointXToInt() - thumbCurvature, thumbOffset.getPointYToInt(), thumbOffset.getPointXToInt() + thumbWidth, thumbOffset.getPointYToInt() + thumbHeight);
        thumbOffset.modify(x, y);
        updateThumbPath();
        invalidateRect
                .fuse(thumbOffset.getPointXToInt() - thumbCurvature, thumbOffset.getPointYToInt(), thumbOffset.getPointXToInt() + thumbWidth, thumbOffset.getPointYToInt() + thumbHeight);
        recyclerView.invalidate();
    }

    public Point getThumbOffset() {
        return thumbOffset;
    }

    // Setter/getter for the thumb bar width for animations
    public void setThumbWidth(int width) {
        invalidateRect
                .set(thumbOffset.getPointXToInt() - thumbCurvature, thumbOffset.getPointYToInt(), thumbOffset.getPointXToInt() + thumbWidth, thumbOffset.getPointYToInt() + thumbHeight);
        thumbWidth = width;
        updateThumbPath();
        invalidateRect
                .fuse(thumbOffset.getPointXToInt() - thumbCurvature, thumbOffset.getPointYToInt(), thumbOffset.getPointXToInt() + thumbWidth, thumbOffset.getPointYToInt() + thumbHeight);
        recyclerView.invalidate();
    }

    public int getThumbWidth() {
        return thumbWidth;
    }

    // Setter/getter for the track bar width for animations
    public void setTrackWidth(int width) {
        invalidateRect.set(thumbOffset.getPointXToInt() - thumbCurvature, 0, thumbOffset.getPointXToInt() + thumbWidth, recyclerView.getHeight());
        trackWidth = width;
        updateThumbPath();
        invalidateRect.fuse(thumbOffset.getPointXToInt() - thumbCurvature, 0, thumbOffset.getPointXToInt() + thumbWidth, recyclerView.getHeight());
        recyclerView.invalidate();
    }

    public int getTrackWidth() {
        return trackWidth;
    }

    public int getThumbHeight() {
        return thumbHeight;
    }

    public int getThumbMaxWidth() {
        return thumbMaxWidth;
    }

    public float getLastTouchY() {
        return lastTouchY;
    }

    public boolean isDraggingThumb() {
        return isDragging;
    }

    public boolean isThumbDetached() {
        return isThumbDetached;
    }

    public void setThumbActiveColor(Color color) {
        thumbActiveColor = color;
        thumbPaint.setColor(color);
        recyclerView.invalidate();
    }

    public void setThumbInactiveColor(Color color) {
        thumbInactiveColor = color;
        thumbPaint.setColor(color);
        recyclerView.invalidate();
    }

    public void setTrackColor(Color color) {
        trackPaint.setColor(color);
        recyclerView.invalidate();
    }

    public void setPopupBackgroundColor(Color color) {
        fastScrollPopup.setBackgroundColor(color);
    }

    public void setPopupTextColor(Color color) {
        fastScrollPopup.setTextColor(color);
    }

    public FastScrollPopup getFastScrollPopup() {
        return fastScrollPopup;
    }

    /**
     * Handles the touch event and determines whether to show the fast scroller (or updates it if
     * it is already showing).
     */
    protected void handleTouchEvent(TouchEvent ev, int downX, int downY, int lastY) {
        ViewConfiguration config = ViewConfiguration.get(recyclerView.getContext());

        int action = ev.getAction();
        int y = (int) ev.getY();
        switch (action) {
            case TouchEvent.PRIMARY_POINT_DOWN:
                if (isNearThumb(downX, downY)) {
                    touchOffset = downY - thumbOffset.y;
                }
                break;
            case TouchEvent.POINT_MOVE:
                // Check if we should start scrolling, but ignore this fastscroll gesture if we have
                // exceeded some fixed movement
                ignoreDragGesture |= Math.abs(y - downY) > config.getScaledPagingTouchSlop();
                if (!isDragging && !ignoreDragGesture && isNearThumb(downX, lastY) &&
                        Math.abs(y - downY) > config.getScaledTouchSlop()) {
                    recyclerView.getParent().requestDisallowInterceptTouchEvent(true);
                    isDragging = true;
                    if (canThumbDetach) {
                        isThumbDetached = true;
                    }
                    touchOffset += (lastY - downY);
                    fastScrollPopup.animateVisibility(true);
                    animateScrollbar(true);
                }
                if (isDragging) {
                    // Update the fastscroller section name at this touch position
                    int top = recyclerView.getBackgroundPadding().top;
                    int bottom = recyclerView.getHeight() - recyclerView.getBackgroundPadding().bottom - thumbHeight;
                    float boundedY = (float) Math.max(top, Math.min(bottom, y - touchOffset));
                    String sectionName = recyclerView.scrollToPositionAtProgress((boundedY - top) / (bottom - top));
                    fastScrollPopup.setSectionName(sectionName);
                    fastScrollPopup.animateVisibility(!sectionName.isEmpty());
                    recyclerView.invalidate(fastScrollPopup.updateFastScrollerBounds(recyclerView, lastY));
                    lastTouchY = boundedY;
                }
                break;
            case TouchEvent.PRIMARY_POINT_UP:
            case TouchEvent.CANCEL:
                touchOffset = 0;
                lastTouchY = 0;
                ignoreDragGesture = false;
                if (isDragging) {
                    isDragging = false;
                    fastScrollPopup.animateVisibility(false);
                    recyclerView.hideScrollBar();
                }
                break;
        }
    }

    protected void draw(Canvas canvas) {
        if (thumbOffset.getPointXToInt() < 0 || thumbOffset.getPointYToInt() < 0) {
            return;
        }

        // Draw the scroll bar track and thumb
        if (trackPaint.getAlpha() > 0) {
            canvas.drawRect(thumbOffset.getPointXToInt(), 0, thumbOffset.getPointXToInt() + thumbWidth, recyclerView.getHeight(), trackPaint);
        }
        canvas.drawPath(thumbPath, thumbPaint);

        // Draw the popup
        fastScrollPopup.draw(canvas);
    }

    /**
     * Animates the width and color of the scrollbar.
     */
    protected void animateScrollbar(boolean isScrolling) {
        if (scrollbarAnimator != null) {
            scrollbarAnimator.cancel();
        }

        scrollbarAnimator = new AnimatorGroup();
        /*ObjectAnimator trackWidthAnim = ObjectAnimator.ofInt(this, "trackWidth",
                isScrolling ? thumbMaxWidth : thumbMinWidth);*/

        AnimatorValue trackWidthAnim = new AnimatorValue();
        trackWidthAnim.setValueUpdateListener(new AnimatorValue.ValueUpdateListener() {
            @Override
            public void onUpdate(AnimatorValue animatorValue, float v) {
                FastScrollBar.this.setTrackWidth(isScrolling ? thumbMaxWidth : thumbMinWidth * (int) v);
            }
        });

        /*ObjectAnimator thumbWidthAnim = ObjectAnimator.ofInt(this, "thumbWidth",
                isScrolling ? thumbMaxWidth : thumbMinWidth);*/

        AnimatorValue thumbWidthAnim = new AnimatorValue();
        thumbWidthAnim.setValueUpdateListener(new AnimatorValue.ValueUpdateListener() {
            @Override
            public void onUpdate(AnimatorValue animatorValue, float v) {
                FastScrollBar.this.setThumbWidth(isScrolling ? thumbMaxWidth : thumbMinWidth * (int) v);
            }
        });

        scrollbarAnimator.runParallel(trackWidthAnim, thumbWidthAnim);


        if (thumbActiveColor != thumbInactiveColor) {
            /*ValueAnimator colorAnimation = ValueAnimator
                    .ofObject(new ArgbEvaluator(), thumbPaint.getColor(), isScrolling ? thumbActiveColor : thumbInactiveColor);
            colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

                @Override
                public void onAnimationUpdate(ValueAnimator animator) {
                    thumbPaint.setColor((Integer) animator.getAnimatedValue());
                    recyclerView
                            .invalidate(thumbOffset.x, thumbOffset.y, thumbOffset.x + thumbWidth, thumbOffset.y + thumbHeight);
                }
            });*/

            AnimatorValue colorAnimation = new AnimatorValue();
            colorAnimation.setValueUpdateListener(new AnimatorValue.ValueUpdateListener() {
                @Override
                public void onUpdate(AnimatorValue animatorValue, float v) {
                    int color = getAnimatedColor(v,thumbPaint.getColor().getValue());
                    thumbPaint.setColor(new Color(color));
                    recyclerView.invalidate();
                }
            });

            scrollbarAnimator.runSerially(colorAnimation);
        }
        scrollbarAnimator.setDuration(SCROLL_BAR_VIS_DURATION);
        scrollbarAnimator.start();
    }

    /**
     * Updates the path for the thumb drawable.
     */
    private void updateThumbPath() {
        thumbCurvature = showThumbCurvature ? thumbMaxWidth - thumbWidth : 0;
        thumbPath.reset();
        thumbPath.moveTo(thumbOffset.getPointXToInt() + thumbWidth, thumbOffset.getPointYToInt());                   // tr
        thumbPath.lineTo(thumbOffset.getPointXToInt() + thumbWidth, thumbOffset.getPointYToInt() + thumbHeight);     // br
        thumbPath.lineTo(thumbOffset.getPointXToInt(), thumbOffset.getPointYToInt() + thumbHeight);                  // bl
        thumbPath.cubicTo(thumbOffset.getPointXToInt(), thumbOffset.getPointYToInt() + thumbHeight,
                thumbOffset.getPointXToInt() - thumbCurvature,
                thumbOffset.getPointYToInt() + thumbHeight / 2,
                thumbOffset.getPointXToInt(), thumbOffset.getPointYToInt());                                             // bl2tl
        thumbPath.close();
    }

    /**
     * Returns whether the specified points are near the scroll bar bounds.
     */
    private boolean isNearThumb(int x, int y) {
        tmpRect.set(thumbOffset.getPointXToInt(), thumbOffset.getPointYToInt(), thumbOffset.getPointXToInt() + thumbWidth,
                thumbOffset.getPointYToInt() + thumbHeight);
       // tmpRect.inset(touchInset, touchInset);
        return tmpRect.contains(x, y, x, y);
    }
}
