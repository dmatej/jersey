/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.glassfish.jersey.examples.sseitemstore.jaxrs;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseBroadcaster;
import jakarta.ws.rs.sse.SseEventSink;

import org.glassfish.jersey.media.sse.SseFeature;


/**
 * A resource for storing named items.
 *
 * @author Marek Potociar
 */
@Path("items")
public class JaxrsItemStoreResource {
    private static final Logger LOGGER = Logger.getLogger(JaxrsItemStoreResource.class.getName());

    private static final ReentrantReadWriteLock storeLock = new ReentrantReadWriteLock();
    private static final LinkedList<String> itemStore = new LinkedList<>();

    private static final AtomicReference<SseBroadcaster> BROADCASTER = new AtomicReference<>(null);

    @Context
    private Sse sse;

    private static volatile long reconnectDelay = 0;

    /**
     * List all stored items.
     *
     * @return list of all stored items.
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String listItems() {
        try {
            storeLock.readLock().lock();
            return itemStore.toString();
        } finally {
            storeLock.readLock().unlock();
        }
    }

    /**
     * Receive & process commands sent by the test client that control the internal resource state.
     * <p>
     * Following is the list of recognized commands:
     * <ul>
     * <li><b>disconnect</b> - disconnect all registered event streams.</li>
     * <li><b>reconnect now</b> - enable client reconnecting.</li>
     * <li><b>reconnect &lt;seconds&gt;</b> - disable client reconnecting.
     * Reconnecting clients will receive a HTTP 503 response with
     * {@value jakarta.ws.rs.core.HttpHeaders#RETRY_AFTER} set to the amount of
     * milliseconds specified.</li>
     * </ul>
     *
     * @param command command to be processed.
     * @return message about processing result.
     * @throws BadRequestException in case the command is not recognized or not specified.
     */
    @POST
    @Path("commands")
    public String processCommand(String command) {
        if (command == null || command.isEmpty()) {
            throw new BadRequestException("No command specified.");
        }

        if ("disconnect".equals(command)) {
            closeBroadcaster();
            return "Disconnected.";
        } else if ("reconnect ".length() < command.length() && command.startsWith("reconnect ")) {
            final String when = command.substring("reconnect ".length());
            try {
                reconnectDelay = "now".equals(when) ? 0 : Long.parseLong(when);
                return "Reconnect strategy updated: " + when;
            } catch (NumberFormatException ignore) {
                // ignored
            }
        }

        throw new BadRequestException("Command not recognized: '" + command + "'");
    }

    /**
     * TODO - rewrite Connect or re-connect to SSE event stream.
     *
     * @param lastEventId Value of custom SSE HTTP <tt>{@value SseFeature#LAST_EVENT_ID_HEADER}</tt> header.
     *                    Defaults to {@code -1} if not set.
     * @throws InternalServerErrorException in case replaying missed events to the reconnected output stream fails.
     * @throws ServiceUnavailableException  in case the reconnect delay is set to a positive value.
     */
    @GET
    @Path("events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void itemEvents(@HeaderParam(SseFeature.LAST_EVENT_ID_HEADER) @DefaultValue("-1") int lastEventId,
                           @Context SseEventSink eventSink) {

        if (lastEventId >= 0) {
            LOGGER.info("Received last event id :" + lastEventId);

            // decide the reconnect handling strategy based on current reconnect delay value.
            final long delay = reconnectDelay;
            if (0 < delay) {
                LOGGER.info("Non-zero reconnect delay [" + delay + "] - responding with HTTP 503.");
                throw new ServiceUnavailableException(delay);
            } else {
                LOGGER.info("Zero reconnect delay - reconnecting.");
                replayMissedEvents(lastEventId, eventSink);
            }
        }

        getBroadcaster().register(eventSink);
    }

    private void replayMissedEvents(int lastEventId, SseEventSink eventSink) {
        try {
            storeLock.readLock().lock();
            final int firstUnreceived = lastEventId + 1;
            final int missingCount = itemStore.size() - firstUnreceived;
            if (missingCount > 0) {
                LOGGER.info("Replaying events - starting with id " + firstUnreceived);
                final ListIterator<String> it = itemStore.subList(firstUnreceived, itemStore.size()).listIterator();
                while (it.hasNext()) {
                    eventSink.send(createItemEvent(it.nextIndex() + firstUnreceived, it.next()));
                }
            } else {
                LOGGER.info("No events to replay.");
            }
        } finally {
            storeLock.readLock().unlock();
        }
    }

    /**
     * Add new item to the item store.
     * <p>
     * Invoking this method will fire 2 new SSE events - 1st about newly added item and 2nd about the new item store size.
     *
     * @param name item name.
     */
    @POST
    public void addItem(@FormParam("name") String name) {
        // Ignore if the request was sent without name parameter.
        if (name == null) {
            return;
        }

        final int eventId;
        try {
            storeLock.writeLock().lock();
            eventId = itemStore.size();
            itemStore.add(name);

            SseBroadcaster sseBroadcaster = getBroadcaster();

            // Broadcasting an un-named event with the name of the newly added item in data
            sseBroadcaster.broadcast(createItemEvent(eventId, name));

            // Broadcasting a named "size" event with the current size of the items collection in data
            final OutboundSseEvent event = sse.newEventBuilder().name("size").data(Integer.class, eventId + 1).build();
            sseBroadcaster.broadcast(event);
        } finally {
            storeLock.writeLock().unlock();
        }
    }

    private OutboundSseEvent createItemEvent(final int eventId, final String name) {
        Logger.getLogger(JaxrsItemStoreResource.class.getName())
                .info("Creating event id [" + eventId + "] name [" + name + "]");
        return sse.newEventBuilder().id("" + eventId).data(String.class, name).build();
    }

    /**
     * Get stored broadcaster or create a new one and store it.
     *
     * @return broadcaster instance.
     */
    private SseBroadcaster getBroadcaster() {
        SseBroadcaster sseBroadcaster = BROADCASTER.get();
        if (sseBroadcaster == null) {
            BROADCASTER.compareAndSet(null, sse.newBroadcaster());
        }

        return BROADCASTER.get();
    }

    /**
     * Close currently stored broadcaster.
     */
    private void closeBroadcaster() {
        SseBroadcaster sseBroadcaster = BROADCASTER.getAndSet(null);
        if (sseBroadcaster == null) {
            return;
        }
        sseBroadcaster.close();
    }
}
