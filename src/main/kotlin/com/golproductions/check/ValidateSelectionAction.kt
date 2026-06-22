package com.golproductions.check

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager

class ValidateSelectionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val command = editor.selectionModel.selectedText?.trim() ?: return
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
                        "$icon Blocked: ${result.reason ?: command.take(80)}"
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
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = CheckSettings.getInstance().enabled
                && editor != null
                && editor.selectionModel.hasSelection()
    }
}
