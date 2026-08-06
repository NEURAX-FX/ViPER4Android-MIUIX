package com.llsl.viper4android.ui.screens.editor

class EditorHistory<T>(
    private val capacity: Int = 50,
) {
    private data class Operation<T>(
        val before: T,
        val after: T,
    )

    private val undoStack = ArrayDeque<Operation<T>>()
    private val redoStack = ArrayDeque<Operation<T>>()
    private var gestureStart: T? = null

    val undoSize: Int get() = undoStack.size
    val redoSize: Int get() = redoStack.size

    fun beginGesture(value: T) {
        gestureStart = value
    }

    fun settleGesture(value: T): Boolean {
        val before = gestureStart ?: return false
        gestureStart = null
        if (before == value) return false
        undoStack.addLast(Operation(before, value))
        while (undoStack.size > capacity.coerceAtLeast(1)) undoStack.removeFirst()
        redoStack.clear()
        return true
    }

    fun undo(): T? {
        val operation = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(operation)
        return operation.before
    }

    fun redo(): T? {
        val operation = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(operation)
        return operation.after
    }

    fun clear() {
        gestureStart = null
        undoStack.clear()
        redoStack.clear()
    }
}
