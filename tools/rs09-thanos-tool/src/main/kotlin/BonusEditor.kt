import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*

object BonusEditor : JFrame("Bonus Editor") {
    val bonusFiels = ArrayList<JTextField>()
    val bonusLabels = arrayOf("Stab ATK: ", "Slash ATK: ", "Crush ATK: ", "Magic ATK: ", "Ranged ATK: ", "Stab DEF: ", "Slash DEF: ", "Crush DEF: ", "Magic DEF: ", "Ranged DEF: ", "Summoning DEF: ", "Strength Bonus: ", "Prayer Bonus: ", "Magic Strength: ", "Ranged Strength: ")
    var initialValues = Array<String>(15) { "0" }
    private var callback: (result: String) -> Unit = {}

    init {
        layout = BorderLayout()
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        defaultCloseOperation = HIDE_ON_CLOSE

        for(bonus in bonusLabels.indices){
            val rowPanel = JPanel(FlowLayout())
            val rowLabel = JLabel(bonusLabels[bonus])
            val rowField = JTextField()
            rowField.text = initialValues[bonus]
            rowLabel.preferredSize = Dimension(200,20)
            rowField.preferredSize = Dimension(100,20)
            bonusFiels.add(rowField)
            rowPanel.add(rowLabel)
            rowPanel.add(rowField)
            panel.add(rowPanel)
            panel.add(JSeparator(JSeparator.HORIZONTAL))
        }

        val buttonPanel = JPanel(BorderLayout())
        buttonPanel.minimumSize = Dimension(300,20)

        val submit = JButton("Submit")
        submit.addActionListener {
            val sb = StringBuilder()
            for(field in bonusFiels.indices){
                sb.append(bonusFiels[field].text)
                if(field != bonusFiels.size - 1) sb.append(",")
            }
            callback.invoke(sb.toString())
            isVisible = false
        }

        val reset = JButton("Reset")
        reset.addActionListener {
            setFieldsToInitial()
            repaint()
        }

        val clear = JButton("Clear")
        clear.addActionListener {
            setInitialValues("")
            setFieldsToInitial()
            repaint()
        }

        buttonPanel.add(reset, BorderLayout.WEST)
        buttonPanel.add(submit, BorderLayout.CENTER)
        buttonPanel.add(clear, BorderLayout.EAST)

        panel.add(buttonPanel)

        add(panel)

        setLocationRelativeTo(null)
        isVisible = false
        pack()
    }

    fun setInitialValues(string: String){
        val split = string.split(",")
        if(split.size > 3){
            for(i in initialValues.indices){
                initialValues[i] = split.getOrElse(i) {"0"}
            }
        } else {
            initialValues = Array(15) {"0"}
        }
        setFieldsToInitial()
    }

    private fun setFieldsToInitial(){
        for(i in bonusFiels.indices){
            bonusFiels[i].text = initialValues[i]
        }
    }

    fun setCallback(method: (result: String) -> Unit){
        callback = method
    }
}