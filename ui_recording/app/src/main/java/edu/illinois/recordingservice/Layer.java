package edu.illinois.recordingservice;

import java.util.ArrayList;
import java.util.HashMap;

class Layer {

    private ArrayList<String> list;
    private HashMap<String, Layer> map;

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
}
