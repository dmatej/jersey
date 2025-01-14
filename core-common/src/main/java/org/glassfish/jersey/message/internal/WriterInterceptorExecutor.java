/*
 * Copyright (c) 2012, 2020 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.message.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;

import org.glassfish.jersey.internal.LocalizationMessages;
import org.glassfish.jersey.internal.PropertiesDelegate;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.inject.InjectionManagerSupplier;
import org.glassfish.jersey.message.MessageBodyWorkers;

/**
 * Represents writer interceptor chain executor for both client and server side.
 * It constructs wrapped interceptor chain and invokes it. At the end of the chain
 * a {@link MessageBodyWriter message body writer} execution interceptor is inserted,
 * which writes entity to the output stream provided by the chain.
 *
 * @author Miroslav Fuksa
 * @author Jakub Podlesak
 */
public final class WriterInterceptorExecutor extends InterceptorExecutor<WriterInterceptor>
        implements WriterInterceptorContext, InjectionManagerSupplier {

    private static final Logger LOGGER = Logger.getLogger(WriterInterceptorExecutor.class.getName());

    private OutputStream outputStream;
    private final MultivaluedMap<String, Object> headers;
    private Object entity;

    private final Iterator<WriterInterceptor> iterator;
    private int processedCount;

    private final InjectionManager injectionManager;


    /**
     * Constructs a new executor to write given type to provided {@link InputStream entityStream}.

     *
     * @param entity entity object to be processed.
     * @param rawType     raw Java entity type.
     * @param type        generic Java entity type.
     * @param annotations array of annotations on the declaration of the artifact
     *            that will be initialized with the produced instance. E.g. if the message
     *            body is to be converted into a method parameter, this will be the
     *            annotations on that parameter returned by
     *            {@code Method.getParameterAnnotations}.
     * @param mediaType media type of the HTTP entity.
     * @param headers mutable HTTP headers associated with HTTP entity.
     * @param propertiesDelegate request-scoped properties delegate.
     * @param entityStream {@link java.io.InputStream} from which an entity will be read. The stream is not
     *            closed after reading the entity.
     * @param workers {@link org.glassfish.jersey.message.MessageBodyWorkers Message body workers}.
     * @param writerInterceptors Writer interceptors that are to be used to intercept writing of an entity.
     * @param injectionManager injection manager.
     */
    public WriterInterceptorExecutor(final Object entity, final Class<?> rawType,
                                     final Type type,
                                     final Annotation[] annotations,
                                     final MediaType mediaType,
                                     final MultivaluedMap<String, Object> headers,
                                     final PropertiesDelegate propertiesDelegate,
                                     final OutputStream entityStream,
                                     final MessageBodyWorkers workers,
                                     final Iterable<WriterInterceptor> writerInterceptors,
                                     final InjectionManager injectionManager) {

        super(rawType, type, annotations, mediaType, propertiesDelegate);
        this.entity = entity;
        this.headers = headers;
        this.outputStream = entityStream;
        this.injectionManager = injectionManager;

        final List<WriterInterceptor> effectiveInterceptors = StreamSupport.stream(writerInterceptors.spliterator(), false)
                                                                           .collect(Collectors.toList());
        effectiveInterceptors.add(new TerminalWriterInterceptor(workers));

        this.iterator = effectiveInterceptors.iterator();
        this.processedCount = 0;
    }

    /**
     * Returns next {@link WriterInterceptor interceptor} in the chain. Stateful method.
     *
     * @return Next interceptor.
     */
    private WriterInterceptor getNextInterceptor() {
        if (!iterator.hasNext()) {
            return null;
        }
        return iterator.next();
    }

    /**
     * Starts the interceptor chain execution.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void proceed() throws IOException {
        final WriterInterceptor nextInterceptor = getNextInterceptor();
        if (nextInterceptor == null) {
            throw new ProcessingException(LocalizationMessages.ERROR_INTERCEPTOR_WRITER_PROCEED());
        }
        traceBefore(nextInterceptor, MsgTraceEvent.WI_BEFORE);
        try {
            nextInterceptor.aroundWriteTo(this);
        } finally {
            processedCount++;
            traceAfter(nextInterceptor, MsgTraceEvent.WI_AFTER);
        }
    }

    @Override
    public Object getEntity() {
        return entity;
    }

    @Override
    public void setEntity(final Object entity) {
        this.entity = entity;
    }

    @Override
    public OutputStream getOutputStream() {
        return this.outputStream;
    }

    @Override
    public void setOutputStream(final OutputStream os) {
        this.outputStream = os;

    }

    @Override
    public MultivaluedMap<String, Object> getHeaders() {
        return this.headers;
    }

    /**
     * Get number of processed interceptors.
     *
     * @return number of processed interceptors.
     */
    int getProcessedCount() {
        return processedCount;
    }

    @Override
    public InjectionManager getInjectionManager() {
        return injectionManager;
    }

    /**
     * Terminal writer interceptor which choose the appropriate {@link MessageBodyWriter}
     * and writes the entity to the output stream. The order of actions is the following: <br>
     * 1. choose the appropriate {@link MessageBodyWriter} <br>
     * 2. if callback is defined then it retrieves size and passes it to the callback <br>
     * 3. writes the entity to the output stream <br>
     *
     */
    private class TerminalWriterInterceptor implements WriterInterceptor {
        private final MessageBodyWorkers workers;

        TerminalWriterInterceptor(final MessageBodyWorkers workers) {
            super();
            this.workers = workers;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void aroundWriteTo(final WriterInterceptorContext context) throws WebApplicationException, IOException {
            processedCount--; //this is not regular interceptor -> count down

            traceBefore(null, MsgTraceEvent.WI_BEFORE);
            try {
                final TracingLogger tracingLogger = getTracingLogger();
                if (tracingLogger.isLogEnabled(MsgTraceEvent.MBW_FIND)) {
                    tracingLogger.log(MsgTraceEvent.MBW_FIND,
                            context.getType().getName(),
                            (context.getGenericType() instanceof Class
                                    ? ((Class) context.getGenericType()).getName() : context.getGenericType()),
                            context.getMediaType(), java.util.Arrays.toString(context.getAnnotations()));
                }

                final MessageBodyWriter writer = workers.getMessageBodyWriter(context.getType(), context.getGenericType(),
                        context.getAnnotations(), context.getMediaType(), WriterInterceptorExecutor.this);

                if (writer == null) {
                    LOGGER.log(Level.SEVERE, LocalizationMessages.ERROR_NOTFOUND_MESSAGEBODYWRITER(
                            context.getMediaType(), context.getType(), context.getGenericType()));
                    throw new MessageBodyProviderNotFoundException(LocalizationMessages.ERROR_NOTFOUND_MESSAGEBODYWRITER(
                            context.getMediaType(), context.getType(), context.getGenericType()));
                }
                invokeWriteTo(context, writer);
            } finally {
                clearLastTracedInterceptor();
                traceAfter(null, MsgTraceEvent.WI_AFTER);
            }
        }

        @SuppressWarnings("unchecked")
        private void invokeWriteTo(final WriterInterceptorContext context, final MessageBodyWriter writer)
                throws WebApplicationException, IOException {
            final TracingLogger tracingLogger = getTracingLogger();
            final long timestamp = tracingLogger.timestamp(MsgTraceEvent.MBW_WRITE_TO);
            final UnCloseableOutputStream entityStream = new UnCloseableOutputStream(context.getOutputStream(), writer);

            try {
                writer.writeTo(context.getEntity(), context.getType(), context.getGenericType(), context.getAnnotations(),
                        context.getMediaType(), context.getHeaders(), entityStream);
            } finally {
                tracingLogger.logDuration(MsgTraceEvent.MBW_WRITE_TO, timestamp, writer);
            }
        }
    }

    /**
     * {@link jakarta.ws.rs.ext.MessageBodyWriter}s should not close the given {@link java.io.OutputStream stream}. This output
     * stream makes sure that the stream is not closed even if MBW tries to do it.
     */
    private static class UnCloseableOutputStream extends OutputStream {

        private final OutputStream original;
        private final MessageBodyWriter writer;

        private UnCloseableOutputStream(final OutputStream original, final MessageBodyWriter writer) {
            this.original = original;
            this.writer = writer;
        }

        @Override
        public void write(final int i) throws IOException {
            original.write(i);
        }

        @Override
        public void write(final byte[] b) throws IOException {
            original.write(b);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            original.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            original.flush();
        }

        @Override
        public void close() throws IOException {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, LocalizationMessages.MBW_TRYING_TO_CLOSE_STREAM(writer.getClass()));
            }
        }
    }
}
