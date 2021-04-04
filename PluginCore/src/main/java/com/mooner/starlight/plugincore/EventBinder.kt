package com.mooner.starlight.plugincore

class EventBinder {
    private var _listener: ((eventName: String, arguments: Map<String, Any>) -> Unit)? = null

    fun bind(eventListener: (eventName: String, arguments: Map<String, Any>) -> Unit) {
        if (this._listener == null) {
            this._listener = eventListener
        }
    }

    fun call(eventName: String, arguments: Map<String, Any>) {
        if (_listener != null) {
            _listener!!(eventName, arguments)
        }
    }
}