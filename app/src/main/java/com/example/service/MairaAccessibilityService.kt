package com.example.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.MairaLogger
import kotlinx.coroutines.*

class MairaAccessibilityService : AccessibilityService() {

    companion object {
        var isServiceConnected = false
            private set
        var instance: MairaAccessibilityService? = null
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected = true
        instance = this
        MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.INFO, "অ্যাক্সেসিবিলিটি সার্ভিস সংযুক্ত হয়েছে")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Automation events monitored when active
    }

    override fun onInterrupt() {
        isServiceConnected = false
        instance = null
        MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.WARN, "অ্যাক্সেসিবিলিটি সার্ভিস বিচ্ছিন্ন হয়েছে")
    }

    override fun onDestroy() {
        isServiceConnected = false
        instance = null
        super.onDestroy()
    }

    fun findNodeByTextOrContentDescription(root: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val lowerQuery = query.lowercase()

        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (!queue.isEmpty()) {
            val node = queue.poll() ?: continue
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""

            if (text.contains(lowerQuery) || desc.contains(lowerQuery)) {
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    fun findEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (!queue.isEmpty()) {
            val node = queue.poll() ?: continue
            if (node.isEditable) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    fun performClickOnNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }
        return false
    }

    fun inputText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // Automation: Tap WhatsApp Send
    fun automateWhatsAppSend(onResult: (Boolean, String) -> Unit) {
        serviceScope.launch {
            var attempts = 0
            var sent = false
            while (attempts < 10 && !sent) {
                delay(1000)
                attempts++
                val root = rootInActiveWindow ?: continue
                val sendButton = findNodeByTextOrContentDescription(root, "send")
                    ?: findNodeByTextOrContentDescription(root, "পাঠাও")
                    ?: findNodeByTextOrContentDescription(root, "পাঠান")

                if (sendButton != null) {
                    performClickOnNode(sendButton)
                    sent = true
                    MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.INFO, "হোয়াটসঅ্যাপ বার্তা পাঠানো বাটন ট্যাপ করা হয়েছে")
                }
            }

            if (sent) {
                onResult(true, "হোয়াটসঅ্যাপ বার্তা পাঠানো হয়েছে।")
            } else {
                onResult(false, "হোয়াটসঅ্যাপ সেন্ড বাটন পাওয়া যায়নি। ড্রাফট রাখা হয়েছে।")
            }
        }
    }
}
