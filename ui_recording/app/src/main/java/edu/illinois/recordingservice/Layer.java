package edu.illinois.recordingservice;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.HashMap;

class Layer {

    private ArrayList<String> list;
    private HashMap<String, Layer> map;

    private ScreenShot screenShot;

    public Layer() {
        list = new ArrayList<String>();
        map = new HashMap<String, Layer>();
    }

    public ArrayList<String> getList() {
        return list;
    }

    public void setList(ArrayList<String> list) {
        this.list = list;
    }

    public HashMap<String, Layer> getMap() {
        return map;
    }

    public void setMap(HashMap<String, Layer> map) {
        this.map = map;
    }

//    public Bitmap getBitmap() {
//        return bitmap;
//    }
//
//    public void setBitmap(Bitmap bitmap) {
//        this.bitmap = bitmap;
//    }

    public ScreenShot getScreenShot() {
        return screenShot;
    }

    public void setScreenShot(ScreenShot screenShot) {
        this.screenShot = screenShot;
    }
}
