package com.jaredrummler.fastscrollrecyclerview;

import ohos.agp.components.Attr;
import ohos.agp.components.AttrSet;
import ohos.agp.components.Component;
import ohos.agp.components.ListContainer;
import ohos.agp.render.Canvas;
import ohos.agp.utils.Color;
import ohos.agp.utils.Rect;
import ohos.app.Context;
import ohos.multimodalinput.event.TouchEvent;

import java.util.Optional;

public class FastScrollRecyclerView extends ListContainer
        implements
        ListContainer.ItemClickedListener,
        Component.DrawTask
{

    private static final int SCROLL_DELTA_THRESHOLD_DP = 4;
    private static final int DEFAULT_HIDE_DELAY = 1000;

    private final ScrollPositionState scrollPositionState = new ScrollPositionState();
    private final Rect backgroundPadding = new Rect();
    FastScrollBar fastScrollBar;
    boolean fastScrollAlwaysEnabled;
    private float deltaThreshold;
    private int hideDelay;
    int lastDy; // Keeps the last known scrolling delta/velocity along y-axis.
    private int downX;
    private int downY;
    private int lastY;

    final Runnable hide = new Runnable() {
        @Override
        public void run() {
            /*if (!fastScrollBar.isDraggingThumb()) {
                fastScrollBar.animateScrollbar(false);
            }*/
        }
    };

    public FastScrollRecyclerView(Context context) {
        this(context, null);
    }

    public FastScrollRecyclerView(Context context, AttrSet attrSet) {
        this(context, attrSet, null);
    }

    public FastScrollRecyclerView(Context context, AttrSet attrs, String styleName) {
        super(context, attrs, styleName);

        Optional<Attr> attr;
        if (attrs != null) {
            attr = attrs.getAttr(Attribute.FAST_SCROLL_ALWAYS_ENABLED);
            fastScrollAlwaysEnabled = attr.map(Attr::getBoolValue).orElse(false);

            attr = attrs.getAttr(Attribute.FAST_SCROLL_HIDE_DELAY);
            hideDelay = attr.map(Attr::getIntegerValue).orElse(DEFAULT_HIDE_DELAY);

            //deltaThreshold = getResources().getDisplayMetrics().density * SCROLL_DELTA_THRESHOLD_DP;
            deltaThreshold = getResourceManager().getDeviceCapability().screenDensity * SCROLL_DELTA_THRESHOLD_DP;

            fastScrollBar = new FastScrollBar(this, attrs);
            fastScrollBar.setDetachThumbOnFastScroll();

            setScrollListener(new ScrollListener() {
                @Override
                public void onScrollFinished() {

                }
            });

            setItemClickedListener(this);
        }
    }

    public void reset() {
        fastScrollBar.reattachThumbToScroll();
    }



    @Override
    public void onItemClicked(ListContainer listContainer, Component component, int i, long l) {

    }

    /**
     * Handles the touch event and determines whether to show the fast scroller (or updates it if
     * it is already showing).
     */
    private boolean handleTouchEvent(TouchEvent ev) {
        int action = ev.getAction();
        int x = (int) ev.getPointerPosition(ev.getIndex()).getX();
        int y = (int) ev.getPointerPosition(ev.getIndex()).getY();
        switch (action) {
            case TouchEvent.PRIMARY_POINT_DOWN:
                // Keep track of the down positions
                downX = x;
                downY = lastY = y;
                if (shouldStopScroll(ev)) {
                    stopScroll();
                }
                fastScrollBar.handleTouchEvent(ev, downX, downY, lastY);
                break;
            case TouchEvent.POINT_MOVE:
                lastY = y;
                fastScrollBar.handleTouchEvent(ev, downX, downY, lastY);
                break;
            case TouchEvent.PRIMARY_POINT_UP:
            case TouchEvent.CANCEL:
                onFastScrollCompleted();
                fastScrollBar.handleTouchEvent(ev, downX, downY, lastY);
                break;
        }
        return fastScrollBar.isDraggingThumb();
    }

    /**
     * Returns whether this {@link TouchEvent} should trigger the scroll to be stopped.
     */
    protected boolean shouldStopScroll(TouchEvent ev) {
        if (ev.getAction() == TouchEvent.PRIMARY_POINT_DOWN) {
            if ((Math.abs(lastDy) < deltaThreshold && getScrollState() != RecyclerView.SCROLL_STATE_IDLE)) {
                // now the touch events are being passed to the {@link WidgetCell} until the
                // touch sequence goes over the touch slop.
                return true;
            }
        }
        return false;
    }

    public void updateBackgroundPadding(Rect padding) {
        backgroundPadding.set(padding.left, padding.top, padding.right, padding.bottom);
    }

    public Rect getBackgroundPadding() {
        return backgroundPadding;
    }

    /**
     * Returns the scroll bar width when the user is scrolling.
     */
    public int getMaxScrollbarWidth() {
        return fastScrollBar.getThumbMaxWidth();
    }

    /**
     * Returns the available scroll height:
     * AvailableScrollHeight = Total height of the all items - last page height
     *
     * This assumes that all rows are the same height.
     */
    protected int getAvailableScrollHeight(int rowCount, int rowHeight) {
        int visibleHeight = getHeight() - backgroundPadding.top - backgroundPadding.bottom;
        int scrollHeight = getPaddingTop() + rowCount * rowHeight + getPaddingBottom();
        return scrollHeight - visibleHeight;
    }

    /**
     * Returns the available scroll bar height:
     * AvailableScrollBarHeight = Total height of the visible view - thumb height
     */
    protected int getAvailableScrollBarHeight() {
        int visibleHeight = getHeight() - backgroundPadding.top - backgroundPadding.bottom;
        return visibleHeight - fastScrollBar.getThumbHeight();
    }

    public boolean isFastScrollAlwaysEnabled() {
        return fastScrollAlwaysEnabled;
    }

    protected void hideScrollBar() {
        if (!fastScrollAlwaysEnabled) {
            removeCallbacks(hide);
            postDelayed(hide, hideDelay);
        }
    }

    public void setThumbActiveColor(Color color) {
        fastScrollBar.setThumbActiveColor(color);
    }

    public void setTrackInactiveColor(Color color) {
        fastScrollBar.setThumbInactiveColor(color);
    }

    public void setPopupBackgroundColor(Color color) {
        fastScrollBar.setPopupBackgroundColor(color);
    }

    public void setPopupTextColor(Color color) {
        fastScrollBar.setPopupTextColor(color);
    }

    public FastScrollBar getFastScrollBar() {
        return fastScrollBar;
    }

    @Override
    public void onDraw(Component component, Canvas canvas) {
        // Draw the ScrollBar AFTER the ItemDecorations are drawn over
        onUpdateScrollbar(0);
        fastScrollBar.draw(canvas);
    }

    /**
     * Updates the scrollbar thumb offset to match the visible scroll of the recycler view.  It does
     * this by mapping the available scroll area of the recycler view to the available space for the
     * scroll bar.
     *
     * @param scrollPosState
     *     the current scroll position
     * @param rowCount
     *     the number of rows, used to calculate the total scroll height (assumes that
     *     all rows are the same height)
     */
    protected void synchronizeScrollBarThumbOffsetToViewScroll(ScrollPositionState scrollPosState, int rowCount) {
        // Only show the scrollbar if there is height to be scrolled
        int availableScrollBarHeight = getAvailableScrollBarHeight();
        int availableScrollHeight = getAvailableScrollHeight(rowCount, scrollPosState.rowHeight);
        if (availableScrollHeight <= 0) {
            fastScrollBar.setThumbOffset(-1, -1);
            return;
        }

        // Calculate the current scroll position, the scrollY of the recycler view accounts for the
        // view padding, while the scrollBarY is drawn right up to the background padding (ignoring
        // padding)
        int scrollY = getPaddingTop() +
                Math.round(((scrollPosState.rowIndex - scrollPosState.rowTopOffset) * scrollPosState.rowHeight));
        int scrollBarY =
                backgroundPadding.top + (int) (((float) scrollY / availableScrollHeight) * availableScrollBarHeight);

        // Calculate the position and size of the scroll bar
        int scrollBarX;
        if (Utilities.isRtl(getResourceManager())) {
            scrollBarX = backgroundPadding.left;
        } else {
            scrollBarX = getWidth() - backgroundPadding.right - fastScrollBar.getThumbWidth();
        }
        fastScrollBar.setThumbOffset(scrollBarX, scrollBarY);
    }

    /**
     * <p>Maps the touch (from 0..1) to the adapter position that should be visible.</p>
     *
     * <p>Override in each subclass of this base class.</p>
     */
    public String scrollToPositionAtProgress(float touchFraction) {
        int itemCount = getAdapter().getItemCount();
        if (itemCount == 0) {
            return "";
        }
        int spanCount = 1;
        int rowCount = itemCount;
        if (getLayoutManager() instanceof GridLayoutManager) {
            spanCount = ((GridLayoutManager) getLayoutManager()).getSpanCount();
            rowCount = (int) Math.ceil((double) rowCount / spanCount);
        }

        // Stop the scroller if it is scrolling
        stopScroll();

        getCurScrollState(scrollPositionState);

        float itemPos = itemCount * touchFraction;

        int availableScrollHeight = getAvailableScrollHeight(rowCount, scrollPositionState.rowHeight);

        //The exact position of our desired item
        int exactItemPos = (int) (availableScrollHeight * touchFraction);

        //Scroll to the desired item. The offset used here is kind of hard to explain.
        //If the position we wish to scroll to is, say, position 10.5, we scroll to position 10,
        //and then offset by 0.5 * rowHeight. This is how we achieve smooth scrolling.
        LinearLayoutManager layoutManager = ((LinearLayoutManager) getLayoutManager());
        layoutManager.scrollToPositionWithOffset(spanCount * exactItemPos / scrollPositionState.rowHeight,
                -(exactItemPos % scrollPositionState.rowHeight));

        if (!(getAdapter() instanceof SectionedAdapter)) {
            return "";
        }

        int posInt = (int) ((touchFraction == 1) ? itemPos - 1 : itemPos);

        SectionedAdapter sectionedAdapter = (SectionedAdapter) getAdapter();
        return sectionedAdapter.getSectionName(posInt);
    }

    /**
     * <p>Updates the bounds for the scrollbar.</p>
     *
     * <p>Override in each subclass of this base class.</p>
     */
    public void onUpdateScrollbar(int dy) {
        int rowCount = getAdapter().getItemCount();
        if (getLayoutManager() instanceof GridLayoutManager) {
            int spanCount = ((GridLayoutManager) getLayoutManager()).getSpanCount();
            rowCount = (int) Math.ceil((double) rowCount / spanCount);
        }
        // Skip early if, there are no items.
        if (rowCount == 0) {
            fastScrollBar.setThumbOffset(-1, -1);
            return;
        }

        // Skip early if, there no child laid out in the container.
        getCurScrollState(scrollPositionState);
        if (scrollPositionState.rowIndex < 0) {
            fastScrollBar.setThumbOffset(-1, -1);
            return;
        }

        synchronizeScrollBarThumbOffsetToViewScroll(scrollPositionState, rowCount);
    }

    /**
     * The current scroll state of the recycler view.  We use this in onUpdateScrollbar()
     * and scrollToPositionAtProgress() to determine the scroll position of the recycler view so
     * that we can calculate what the scroll bar looks like, and where to jump to from the fast
     * scroller.
     */
    public static class ScrollPositionState {

        // The index of the first visible row
        public int rowIndex;
        // The offset of the first visible row, in percentage of the height
        public float rowTopOffset;
        // The height of a given row (they are currently all the same height)
        public int rowHeight;
    }
}
