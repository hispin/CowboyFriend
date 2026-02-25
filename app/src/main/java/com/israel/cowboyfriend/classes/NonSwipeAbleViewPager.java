package com.israel.cowboyfriend.classes;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.viewpager.widget.ViewPager;

public class NonSwipeAbleViewPager extends ViewPager {

    public NonSwipeAbleViewPager(Context context) {
        super(context);
    }

    public NonSwipeAbleViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // Never allow swiping to switch between pages
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        performClick();
        // Never allow swiping to switch between pages
        return false;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
