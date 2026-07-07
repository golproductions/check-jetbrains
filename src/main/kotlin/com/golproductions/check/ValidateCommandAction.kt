package com.golproductions.check

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages

class ValidateCommandAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val command = Messages.showInputDialog(
            project,
            "Enter a shell command to validate:",
            "GOL Check",
            null
        ) ?: return

        if (command.isBlank()) return

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val result = CheckApi.validate(command)
                ApplicationManager.getApplication().invokeLater {
                    val type = if (result.verdict == "runnable") NotificationType.INFORMATION else NotificationType.WARNING
                    val icon = if (result.verdict == "runnable") "✓" else "✗"
                    val msg = if (result.verdict == "runnable") {
                        "$icon Runnable: ${command.take(80)}"
                    } else {
                        "$icon Invalid: ${result.reason ?: command.take(80)}"
                    }
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("GOL Check")
                        .createNotification(msg, type)
                        .notify(project)
                }
            } catch (ex: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("GOL Check")
                        .createNotification("Check error: ${ex.message}", NotificationType.ERROR)
                        .notify(project)
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = CheckSettings.getInstance().enabled
    }
}
