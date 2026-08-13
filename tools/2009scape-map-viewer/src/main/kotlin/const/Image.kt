package const

import javax.imageio.ImageIO
import javax.swing.ImageIcon

object Image {
    val RED_DOT = ImageIcon(ImageIO.read(javaClass.getResource("/dot_red.png")))
    val YELLOW_DOT = ImageIcon(ImageIO.read(javaClass.getResource("/dot_yellow.png")))
    val DELETE_HI = ImageIcon(ImageIO.read(javaClass.getResource("/trash_hi.png")))
    val DELTE_LO = ImageIcon(ImageIO.read(javaClass.getResource("/trash_dark.png")))
    val ADD_HI = ImageIcon(ImageIO.read(javaClass.getResource("/add_hi.png")))
    val ADD_LO = ImageIcon(ImageIO.read(javaClass.getResource("/add_dark.png")))
}