package edu.illinois.odim

class Layer {
    var list: ArrayList<String>
        get() = this.list
        set(list: ArrayList<String>) {
            this.list = list
        }

    var map: HashMap<String, Layer>
        get() = this.map
        set(map: HashMap<String, Layer>){
            this.map = map
        }

    var screenShot: ScreenShot
        get() = this.screenShot
        set(screenShot: ScreenShot) {
            this.screenShot = screenShot
        }

    constructor() {
        this.list = ArrayList<String>()
        this.map = HashMap<String, Layer>()
    }
}