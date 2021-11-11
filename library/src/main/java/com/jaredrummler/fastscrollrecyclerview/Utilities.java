/*
 * Copyright (C) 2016 Jared Rummler <jared.rummler@gmail.com>
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

import ohos.agp.colors.RgbColor;
import ohos.global.resource.ResourceManager;

final class Utilities {

  /*static boolean isRtl(Resources res) {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 &&
        res.getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
  }*/

  static boolean isRtl(ResourceManager res) {
    return res.getConfiguration().isLayoutRTL;
  }

  /**
   * get the animated value with fraction and values
   *
   * @param fraction 0~1
   * @param colors color array
   * @return int color
   */
  public static int getAnimatedColor(float fraction, int... colors){
    if(colors == null || colors.length == 0){
      return 0;
    }
    if(colors.length == 1){
      return getAnimatedColor(0,colors[0],fraction);
    }else{
      if(fraction == 1){
        return colors[colors.length-1];
      }
      float oneFraction = 1f / (colors.length - 1);
      float offFraction = 0;
      for (int i = 0; i < colors.length - 1; i++) {
        if (offFraction + oneFraction >= fraction) {
          return getAnimatedColor(colors[i], colors[i + 1], (fraction - offFraction) * (colors.length -1));
        }
        offFraction += oneFraction;
      }
    }
    return 0;
  }

  /**
   * get the animated color with start color, end color and fraction
   *
   * @param fraction 0~1
   * @param from start color
   * @param to end color
   * @return int color
   */
  public static int getAnimatedColor(int from, int to, float fraction) {
    RgbColor colorFrom = RgbColor.fromArgbInt(from);
    RgbColor colorTo = RgbColor.fromArgbInt(to);
    int red = (int) (colorFrom.getRed() + (colorTo.getRed() - colorFrom.getRed()) * fraction);
    int blue = (int) (colorFrom.getBlue() + (colorTo.getBlue() - colorFrom.getBlue()) * fraction);
    int green = (int) (colorFrom.getGreen() + (colorTo.getGreen() - colorFrom.getGreen()) * fraction);
    int alpha = (int) (colorFrom.getAlpha() + (colorTo.getAlpha() - colorFrom.getAlpha()) * fraction);
    RgbColor mCurrentColorRgb = new RgbColor(red, green, blue, alpha);
    return mCurrentColorRgb.asArgbInt();
  }

}
