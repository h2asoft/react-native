/**
 * Copyright (c) 2015-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.react.uimanager;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import android.view.View;
import android.view.ViewGroup;

import javax.annotation.Nullable;

/**
 * Class providing children management API for view managers of classes extending ViewGroup.
 */
public abstract class ViewGroupManager <T extends ViewGroup>
    extends BaseViewManager<T, LayoutShadowNode> {

  private static WeakHashMap<View, Integer> mZIndexHash = new WeakHashMap<>();
  // This is used to remove views from the React view hierarchy but keep them in the Native one.
  // Mapping from the parent view to a list of its children.
  private static WeakHashMap<View, List<View>> mReactChildrenMap = new WeakHashMap<>();

  @Override
  public LayoutShadowNode createShadowNodeInstance() {
    return new LayoutShadowNode();
  }

  @Override
  public Class<? extends LayoutShadowNode> getShadowNodeClass() {
    return LayoutShadowNode.class;
  }

  @Override
  public void updateExtraData(T root, Object extraData) {
  }

  public void addView(T parent, View child, int index) {
    List<View> reactChildren = mReactChildrenMap.get(parent);
    if (reactChildren != null) {
      reactChildren.add(index, child);
      if (index == reactChildren.size() - 1) {
        parent.addView(child);
      } else {
        View addBeforeView = reactChildren.get(index + 1);
        int addBeforeIndex = parent.indexOfChild(addBeforeView);
        parent.addView(child, addBeforeIndex - 1);
      }
    } else {
      parent.addView(child, index);
    }
  }

  /**
   * Convenience method for batching a set of addView calls
   * Note that this adds the views to the beginning of the ViewGroup
   *
   * @param parent the parent ViewGroup
   * @param views the set of views to add
   */
  public void addViews(T parent, List<View> views) {
    for (int i = 0, size = views.size(); i < size; i++) {
      addView(parent, views.get(i), i);
    }
  }

  public static void setViewZIndex(View view, int zIndex) {
    mZIndexHash.put(view, zIndex);
  }

  public static @Nullable Integer getViewZIndex(View view) {
    return mZIndexHash.get(view);
  }

  public int getChildCount(T parent) {
    List<View> reactChildren = mReactChildrenMap.get(parent);
    if (reactChildren != null) {
      return reactChildren.size();
    } else {
      return parent.getChildCount();
    }
  }

  public View getChildAt(T parent, int index) {
    List<View> reactChildren = mReactChildrenMap.get(parent);
    if (reactChildren != null) {
      return reactChildren.get(index);
    } else {
      return parent.getChildAt(index);
    }
  }

  public void removeViewAt(T parent, int index) {
    List<View> reactChildren = mReactChildrenMap.get(parent);
    if (reactChildren != null) {
      View removedView = reactChildren.remove(index);
      if (removedView != null) {
        parent.removeView(removedView);
      }
      mReactChildrenMap.remove(removedView);
    } else {
      View removedView = parent.getChildAt(index);
      parent.removeViewAt(index);
      mReactChildrenMap.remove(removedView);
    }
  }

  public void removeView(T parent, View view) {
    for (int i = 0; i < getChildCount(parent); i++) {
      if (getChildAt(parent, i) == view) {
        removeViewAt(parent, i);
        break;
      }
    }
  }

  public void removeAllViews(T parent) {
    for (int i = getChildCount(parent) - 1; i >= 0; i--) {
      removeViewAt(parent, i);
    }
  }

  /**
   * Remove a view from the React view hierarchy but keep the actual native view there. Use
   * {@link ViewGroupManager#removeNativeView} to remove it completely.
   */
  public void removeReactViewAt(T parent, int index) {
    List<View> reactChildren = mReactChildrenMap.get(parent);

    // Create the React children mapping lazily the first time it diverges from native.
    if (reactChildren == null) {
      reactChildren = new ArrayList<>(parent.getChildCount());
      for (int i = 0; i < parent.getChildCount(); i++) {
        reactChildren.add(parent.getChildAt(i));
      }
      mReactChildrenMap.put(parent, reactChildren);
    }
    reactChildren.remove(index);
  }

  /**
   * Remove a view that was previously removed from the React view hierarchy but not from native.
   */
  public void removeNativeView(T parent, View view) {
    List<View> reactChildren = mReactChildrenMap.get(parent);
    if (reactChildren != null) {
      reactChildren.remove(view);
    }
    parent.removeView(view);
  }

  /**
   * Returns whether this View type needs to handle laying out its own children instead of
   * deferring to the standard css-layout algorithm.
   * Returns true for the layout to *not* be automatically invoked. Instead onLayout will be
   * invoked as normal and it is the View instance's responsibility to properly call layout on its
   * children.
   * Returns false for the default behavior of automatically laying out children without going
   * through the ViewGroup's onLayout method. In that case, onLayout for this View type must *not*
   * call layout on its children.
   */
  public boolean needsCustomLayoutForChildren() {
    return false;
  }

  /**
   * Returns whether or not this View type should promote its grandchildren as Views. This is an
   * optimization for Scrollable containers when using Nodes, where instead of having one ViewGroup
   * containing a large number of draw commands (and thus being more expensive in the case of
   * an invalidate or re-draw), we split them up into several draw commands.
   */
  public boolean shouldPromoteGrandchildren() {
    return false;
  }
}
