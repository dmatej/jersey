/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.jersey.internal.util;


import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.glassfish.jersey.internal.jsr166.Flow;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test Jersey {@link Flow.Publisher} implementation, {@link JerseyPublisher}.
 *
 * @author Adam Lindenthal
 */
public class JerseyPublisherTest {

    @Test
    public void test() throws InterruptedException {

        final CountDownLatch openLatch1 = new CountDownLatch(1);
        final CountDownLatch openLatch2 = new CountDownLatch(1);
        final CountDownLatch openLatch3 = new CountDownLatch(1);

        final CountDownLatch writeLatch1 = new CountDownLatch(3);
        final CountDownLatch writeLatch2 = new CountDownLatch(2);
        final CountDownLatch writeLatch3 = new CountDownLatch(1);

        final CountDownLatch closeLatch = new CountDownLatch(3);

        final JerseyPublisher<String> publisher = new JerseyPublisher<>(JerseyPublisher.PublisherStrategy.BLOCKING);
        final PublisherTestSubscriber subscriber1 =
                new PublisherTestSubscriber("SUBSCRIBER-1", openLatch1, writeLatch1, closeLatch);
        final PublisherTestSubscriber subscriber2 =
                new PublisherTestSubscriber("SUBSCRIBER-2", openLatch2, writeLatch2, closeLatch);
        final PublisherTestSubscriber subscriber3 =
                new PublisherTestSubscriber("SUBSCRIBER-3", openLatch3, writeLatch3, closeLatch);

        publisher.publish("START");  // sent before any subscriber subscribed - should not be received

        publisher.subscribe(subscriber1);
        publisher.publish("Zero");   // before receive, but should be received by SUBSCRIBER-1
        assertTrue(openLatch1.await(200, TimeUnit.MILLISECONDS));

        subscriber1.receive(3);
        publisher.publish("One");    // should be received by SUBSCRIBER-1

        publisher.subscribe(subscriber2);
        assertTrue(openLatch2.await(200, TimeUnit.MILLISECONDS));
        subscriber2.receive(5);

        publisher.publish("Two");    // should be received by SUBSCRIBER-1 and SUBSCRIBER-2

        publisher.subscribe(subscriber3);
        assertTrue(openLatch3.await(200, TimeUnit.MILLISECONDS));
        subscriber3.receive(5);

        publisher.publish("Three");  // should be received by SUBSCRIBER-2 and SUBSCRIBER-3

        assertTrue(writeLatch1.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(writeLatch2.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(writeLatch3.await(1000, TimeUnit.MILLISECONDS));

        Queue<String> result = subscriber1.getReceivedData();
        assertEquals(3, result.size());
        assertEquals("Zero", result.poll());
        assertEquals("One", result.poll());
        assertEquals("Two", result.poll());

        result = subscriber2.getReceivedData();
        assertEquals(2, result.size());
        assertEquals("Two", result.poll());
        assertEquals("Three", result.poll());

        result = subscriber3.getReceivedData();
        assertEquals(1, result.size());
        assertEquals("Three", result.poll());

        publisher.close();
        subscriber1.receive(1);     // --> with this, the CDL is successfully counted down and await returns true
        assertTrue(closeLatch.await(10000, TimeUnit.MILLISECONDS));
        // this behaviour is a little counter-intuitive, but confirmed as correct by Flow.SubmissionPublisher author,
        // Dough Lea on the JDK mailing list
    }

    @Test
    public void testNonBlocking() throws InterruptedException {
        final int MSG_COUNT = 300;
        final int DATA_COUNT = Flow.defaultBufferSize() + 2;
        final int WAIT_TIME = 20 * DATA_COUNT;

        final JerseyPublisher<String> publisher = new JerseyPublisher<>();

        final CountDownLatch openLatchActive = new CountDownLatch(1);
        final CountDownLatch writeLatch = new CountDownLatch(DATA_COUNT);
        final CountDownLatch closeLatch = new CountDownLatch(1);

        final CountDownLatch openLatchDead = new CountDownLatch(1);

        final PublisherTestSubscriber deadSubscriber =
                new PublisherTestSubscriber("dead", openLatchDead, new CountDownLatch(0), new CountDownLatch(0));

        final PublisherTestSubscriber activeSubscriber =
                new PublisherTestSubscriber("active", openLatchActive, writeLatch, closeLatch);

        // subscribe to publisher
        publisher.subscribe(deadSubscriber);
        assertTrue(openLatchDead.await(200, TimeUnit.MILLISECONDS));
        publisher.subscribe(activeSubscriber);
        assertTrue(openLatchActive.await(200, TimeUnit.MILLISECONDS));

        activeSubscriber.receive(1000);

        AtomicInteger counter = new AtomicInteger(0);
        final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleWithFixedDelay(() -> {
            int i = counter.getAndIncrement();

            if (i >= MSG_COUNT) {
                scheduledExecutorService.shutdown();
                return;
            }

            publisher.publish("MSG-" + i);
        }, 0, 10, TimeUnit.MILLISECONDS);

        writeLatch.await(WAIT_TIME, TimeUnit.MILLISECONDS);
        assertTrue(writeLatch.getCount() <= 1);

        assertTrue(DATA_COUNT - activeSubscriber.getReceivedData().size() <= 1);
        assertEquals(0, deadSubscriber.getReceivedData().size());

        assertFalse(activeSubscriber.hasError());
        assertTrue(deadSubscriber.hasError());

        publisher.close();

        assertTrue(closeLatch.await(WAIT_TIME, TimeUnit.MILLISECONDS));
        assertTrue(activeSubscriber.isCompleted());
        assertFalse(deadSubscriber.isCompleted());
    }

    @Test
    public void testCascadingClose() throws InterruptedException {
        final CountDownLatch openLatch = new CountDownLatch(1);
        final CountDownLatch writeLatch = new CountDownLatch(1);
        final CountDownLatch closeLatch = new CountDownLatch(1);

        final JerseyPublisher<String> publisher =
            new JerseyPublisher<>(JerseyPublisher.PublisherStrategy.BLOCKING);
        final PublisherTestSubscriber subscriber =
            new PublisherTestSubscriber("SUBSCRIBER", openLatch, writeLatch, closeLatch);
        publisher.subscribe(subscriber);
        assertTrue(openLatch.await(200, TimeUnit.MILLISECONDS));

        subscriber.receive(1);
        publisher.publish("Zero");
        assertTrue(writeLatch.await(1000, TimeUnit.MILLISECONDS));

        publisher.close(false);     // must not call onComplete()
        Thread.sleep(10000);
        assertFalse(subscriber.isCompleted());
    }

    class PublisherTestSubscriber implements Flow.Subscriber<String> {

        private final String name;
        private final CountDownLatch openLatch;
        private final CountDownLatch writeLatch;
        private final CountDownLatch closeLatch;
        private Flow.Subscription subscription;
        private final Queue<String> data;
        private boolean hasError = false;
        private boolean completed = false;

        PublisherTestSubscriber(final String name,
                                final CountDownLatch openLatch,
                                final CountDownLatch writeLatch,
                                final CountDownLatch closeLatch) {
            this.name = name;
            this.openLatch = openLatch;
            this.writeLatch = writeLatch;
            this.closeLatch = closeLatch;
            this.data = new ConcurrentLinkedQueue<>();
        }

        @Override
        public void onSubscribe(final Flow.Subscription subscription) {
            this.subscription = subscription;
            openLatch.countDown();
        }

        @Override
        public void onNext(final String item) {
            data.add(item);
            writeLatch.countDown();
        }

        @Override
        public void onError(final Throwable throwable) {
            throwable.printStackTrace();
            hasError = true;
        }

        @Override
        public void onComplete() {
            completed = true;
            closeLatch.countDown();
        }

        @Override
        public String toString() {
            return this.name + " " + Thread.currentThread().getName();
        }

        public void receive(final long n) {
            if (subscription != null) {
                subscription.request(n);
            }
        }

        /**
         * Retrieve stored (received) data for assertions.
         *
         * @return all the data received by subscriber.
         */
        Queue<String> getReceivedData() {
            return this.data;
        }

        boolean hasError() {
            return hasError;
        }

        boolean isCompleted() {
            return completed;
        }
    }
}
