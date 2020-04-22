package edu.illinois.recordingservice;

import android.graphics.Bitmap;
import android.graphics.Rect;

public class ScreenShot {
    public static final int TYPE_CLICK = 0;
    public static final int TYPE_SCROLL = 1;

    private Bitmap bitmap;
    private Rect rect;
    private int action_type;

    public ScreenShot(Bitmap bitmap, Rect rect, int action_type) {
        this.bitmap = bitmap;
        this.rect = rect;
        this.action_type = action_type;
    }


    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public Rect getRect() {
        return rect;
    }

    public void setRect(Rect rect) {
        this.rect = rect;
    }

    public int getAction_type() {
        return action_type;
    }

    public void setAction_type(int action_type) {
        this.action_type = action_type;
    }
}
