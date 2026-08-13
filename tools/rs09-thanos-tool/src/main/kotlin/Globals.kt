import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

val confirmation = object : JFrame("Notice"){
    init {
        layout = BorderLayout()
        minimumSize = Dimension(250,100)
        defaultCloseOperation = HIDE_ON_CLOSE
        val okButton = JButton("OK")
        okButton.addActionListener {
            isVisible = false
        }
        add(JLabel("Added a new row to the table."), BorderLayout.NORTH)
        add(okButton, BorderLayout.SOUTH)
        setLocationRelativeTo(null)
        isVisible = false
        pack()
    }
}
val saveConfirmation = object : JFrame("Notice"){
    init {
        minimumSize = Dimension(250,100)
        defaultCloseOperation = HIDE_ON_CLOSE
        layout = BorderLayout()
        val okButton = JButton("OK")
        okButton.addActionListener {
            isVisible = false
        }
        add(JLabel("Data saved."), BorderLayout.NORTH)
        add(okButton, BorderLayout.SOUTH)
        isVisible = false
        setLocationRelativeTo(null)
        pack()
    }
}

val credits = object : JFrame("Credits"){
    init {
        defaultCloseOperation = HIDE_ON_CLOSE
        val creditPanel = JPanel()
        creditPanel.layout = BoxLayout(creditPanel, BoxLayout.PAGE_AXIS)
        layout = BorderLayout()

        for(credit in EditorConstants.CREDITS){
            creditPanel.add(JLabel(credit))
        }

        add(creditPanel)

        isVisible = false

        setLocationRelativeTo(null)

        pack()
    }
}