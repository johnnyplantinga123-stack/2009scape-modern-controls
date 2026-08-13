package ui

import javax.swing.JButton

class PlaneButton(val plane: Int) : JButton(plane.toString()){
    init {
        addActionListener {
            if(Rs2MapEditor.plane != this.plane){
                Rs2MapEditor.plane = this.plane
                Rs2MapEditor.npcPanel.redrawRows()
                Rs2MapEditor.itemPanel.redrawRows()
                Rs2MapEditor.mapPanel.repaintMap()
            }
        }
    }
}