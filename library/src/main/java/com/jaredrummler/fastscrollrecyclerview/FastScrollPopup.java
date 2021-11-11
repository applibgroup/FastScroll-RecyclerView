/*
 * Copyright (C) 2016 Jared Rummler <jared.rummler@gmail.com>
 * Copyright (C) 2015 The Android Open Source Project
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
 *
 */

package com.jaredrummler.fastscrollrecyclerview;

/*import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import androidx.annotation.ColorInt;*/

import ohos.agp.animation.Animator;
import ohos.agp.animation.AnimatorProperty;
import ohos.agp.components.Attr;
import ohos.agp.components.AttrSet;
import ohos.agp.components.element.Element;
import ohos.agp.components.element.VectorElement;
import ohos.agp.render.Canvas;
import ohos.agp.render.Paint;
import ohos.agp.utils.Color;
import ohos.agp.utils.Rect;
import ohos.global.resource.NotExistException;
import ohos.global.resource.ResourceManager;
import ohos.global.resource.WrongTypeException;

import java.io.IOException;
import java.util.Optional;

/**
 * The fast scroller popup that shows the section name the list will jump to.
 */
public class FastScrollPopup {

  private static final float FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR = 1.5f;

  private Color bgColor;
  private Color textColor;
  private float textSize;

  private final Rect backgroundBounds = new Rect(); // The absolute bounds of the fast scroller bg
  private final Rect invalidateRect = new Rect();
  private final Rect tmpRect = new Rect();
  private final Rect textBounds = new Rect();

  private FastScrollRecyclerView recyclerView;
  private AnimatorProperty alphaAnimator;
  private ResourceManager resources;
  private Element background;
  private Paint textPaint;
  private String sectionName;
  private int originalBackgroundSize;
  private float alpha;
  private boolean visible;

  public FastScrollPopup(FastScrollRecyclerView rv, AttrSet attrs) {
    recyclerView = rv;
    resources = rv.getResources();

    Optional<Attr> attr;
    if (attrs != null) {
      attr = attrs.getAttr(Attribute.FAST_SCROLL_POPUP_BACKGROUND_COLOR);
      bgColor = attr.map(Attr::getColorValue).orElse(Color.TRANSPARENT);

      attr = attrs.getAttr(Attribute.FAST_SCROLL_POPUP_TEXT_COLOR);
      textColor = attr.map(Attr::getColorValue).orElse(Color.WHITE);

      attr = attrs.getAttr(Attribute.FAST_SCROLL_TEXT_SIZE);
      textSize = attr.map(Attr::getDimensionValue).orElse(ResourceTable.Float_fastscroll_popup_text_size);

      attr = attrs.getAttr(Attribute.FAST_SCROLL_POPUP_PADDING);
      originalBackgroundSize = (int) textSize + attr.map(Attr::getDimensionValue).orElse(ResourceTable.Float_fastscroll_popup_default_padding);
    }

    try {
      background = new VectorElement(getContext(), ResourceTable.Graphic_fastscroll_popup_bg);
    } catch (IOException | NotExistException | WrongTypeException e) {
      e.printStackTrace();
    }

    if (bgColor != Color.TRANSPARENT) {
      /*background = background.mutate();
      background.setColorFilter(bgColor, PorterDuff.Mode.SRC_IN);*/
    }

    background.setBounds(0, 0, originalBackgroundSize, originalBackgroundSize);
    textPaint = new Paint();
    textPaint.setColor(textColor);
    textPaint.setAntiAlias(true);
    textPaint.setTextSize((int) textSize);

  }

  /**
   * Sets the section name.
   */
  protected void setSectionName(String sectionName) {
    if (!sectionName.equals(this.sectionName)) {
      this.sectionName = sectionName;
      textPaint.getTextBounds(sectionName);
      // Update the width to use measureText since that is more accurate
      textBounds.right = (int) (textBounds.left + textPaint.measureText(sectionName));
    }
  }

  /**
   * Updates the bounds for the fast scroller.
   *
   * @return the invalidation rect for this update.
   */
  protected Rect updateFastScrollerBounds(FastScrollRecyclerView rv, int lastTouchY) {
    invalidateRect.set(backgroundBounds.left, backgroundBounds.top, backgroundBounds.right, backgroundBounds.bottom);

    if (isVisible()) {
      // Calculate the dimensions and position of the fast scroller popup
      int edgePadding = rv.getMaxScrollbarWidth();
      int bgPadding = (originalBackgroundSize - textBounds.getHeight()) / 2;
      int bgHeight = originalBackgroundSize;
      int bgWidth = Math.max(originalBackgroundSize, textBounds.getWidth() + (2 * bgPadding));
      if (Utilities.isRtl(resources)) {
        backgroundBounds.left = rv.getBackgroundPadding().left + (2 * rv.getMaxScrollbarWidth());
        backgroundBounds.right = backgroundBounds.left + bgWidth;
      } else {
        backgroundBounds.right = rv.getWidth() - rv.getBackgroundPadding().right - (2 * rv.getMaxScrollbarWidth());
        backgroundBounds.left = backgroundBounds.right - bgWidth;
      }
      backgroundBounds.top = lastTouchY - (int) (FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR * bgHeight);
      backgroundBounds.top =
          Math.max(edgePadding, Math.min(backgroundBounds.top, rv.getHeight() - edgePadding - bgHeight));
      backgroundBounds.bottom = backgroundBounds.top + bgHeight;
    } else {
      backgroundBounds.set(0,0,0,0);
    }

    // Combine the old and new fast scroller bounds to create the full invalidate rect
    invalidateRect.fuse(backgroundBounds);
    return invalidateRect;
  }

  /**
   * Animates the visibility of the fast scroller popup.
   */
  public void animateVisibility(boolean visible) {
    if (this.visible != visible) {
      this.visible = visible;
      if (alphaAnimator != null) {
        alphaAnimator.cancel();
      }
      alphaAnimator = new AnimatorProperty();
      alphaAnimator.alpha(visible ? 1f : 0f);
      alphaAnimator.setDuration(visible ? 200 : 150);
      alphaAnimator.start();
    }
  }

  // Setter/getter for the popup alpha for animations
  public void setAlpha(float alpha) {
    this.alpha = alpha;
    recyclerView.invalidate();
  }

  public float getAlpha() {
    return alpha;
  }

  public void setBackgroundColor(Color color) {
    background = background.mutate();
    background.setColorFilter(color, PorterDuff.Mode.SRC_IN);
  }

  public void setTextColor(Color color) {
    textPaint.setColor(color);
  }

  public int getHeight() {
    return originalBackgroundSize;
  }

  protected void draw(Canvas c) {
    if (isVisible()) {
      // Draw the fast scroller popup
      int restoreCount = c.save();
      c.translate(backgroundBounds.left, backgroundBounds.top);
      tmpRect.set(backgroundBounds.left, backgroundBounds.top, backgroundBounds.right, backgroundBounds.bottom);
      tmpRect.offset(0, 0);
      background.setBounds(tmpRect);
      background.setAlpha((int) (alpha * 255));
      background.drawToCanvas(c);
      textPaint.setAlpha((int) (alpha * 255));

      c.drawText(textPaint, sectionName,
              (backgroundBounds.getWidth() - textBounds.getWidth()) / 2,
              backgroundBounds.getHeight() - (backgroundBounds.getHeight() - textBounds.getHeight()) / 2);

      c.restoreToCount(restoreCount);
    }
  }

  public boolean isVisible() {
    return (alpha > 0f) && (sectionName != null);
  }

}
