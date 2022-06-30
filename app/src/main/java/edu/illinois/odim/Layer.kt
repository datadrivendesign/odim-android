package edu.illinois.odim

class Layer {
    private var list: ArrayList<String>
        get() = this.list
        set(list: ArrayList<String>) {
            this.list = list
        }

    private var map: HashMap<String, Layer>
        get() = this.map
        set(map: HashMap<String, Layer>){
            this.map = map
        }

    private var screenShot: ScreenShot
        get() = this.screenShot
        set(screenShot: ScreenShot) {
            this.screenShot = screenShot
        }
}