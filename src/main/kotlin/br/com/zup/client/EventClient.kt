package br.com.zup.client

import com.google.common.base.Preconditions
import com.netflix.conductor.client.config.DefaultConductorClientConfiguration
import com.netflix.conductor.client.http.ClientBase
import com.netflix.conductor.common.metadata.events.EventHandler
import com.sun.jersey.api.client.ClientHandler
import com.sun.jersey.api.client.config.DefaultClientConfig

class EventClient : ClientBase(DefaultClientConfig(), DefaultConductorClientConfiguration(), null as ClientHandler?) {

    fun addEventHandler(eventHandler: EventHandler) {
        Preconditions.checkNotNull(eventHandler, "Event handler cannot be null")
        this.postForEntityWithRequestOnly("event", eventHandler)
    }

    fun removeEventHandler(eventName: String) {
        delete("event/{eventName}", eventName)
    }

}
