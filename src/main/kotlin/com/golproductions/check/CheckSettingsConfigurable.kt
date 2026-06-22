package com.golproductions.check

import com.intellij.openapi.options.Configurable
import javax.swing.*

class CheckSettingsConfigurable : Configurable {
    private var panel: JPanel? = null
    private var clientIdField: JTextField? = null
    private var enabledCheckbox: JCheckBox? = null

    override fun getDisplayName() = "GOL Check"

    override fun createComponent(): JComponent {
        val p = JPanel()
        p.layout = BoxLayout(p, BoxLayout.Y_AXIS)

        val idPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.X_AXIS) }
        idPanel.add(JLabel("Client ID: "))
        clientIdField = JTextField(30).also { idPanel.add(it) }

        enabledCheckbox = JCheckBox("Enable Check validation", true)

        val linkLabel = JLabel("<html>Get your Client ID at <a href='#'>golproductions.com/check</a></html>")

        p.add(idPanel)
        p.add(Box.createVerticalStrut(8))
        p.add(enabledCheckbox)
        p.add(Box.createVerticalStrut(8))
        p.add(linkLabel)

        panel = p
        reset()
        return p
    }

    override fun isModified(): Boolean {
        val settings = CheckSettings.getInstance()
        return clientIdField?.text != settings.clientId || enabledCheckbox?.isSelected != settings.enabled
    }

    override fun apply() {
        val settings = CheckSettings.getInstance()
        settings.clientId = clientIdField?.text ?: ""
        settings.enabled = enabledCheckbox?.isSelected ?: true
    }

    override fun reset() {
        val settings = CheckSettings.getInstance()
        clientIdField?.text = settings.clientId
        enabledCheckbox?.isSelected = settings.enabled
    }

    override fun disposeUIResources() {
        panel = null
        clientIdField = null
        enabledCheckbox = null
    }
}
