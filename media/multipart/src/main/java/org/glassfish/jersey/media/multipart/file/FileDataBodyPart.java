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

package org.glassfish.jersey.media.multipart.file;

import java.io.File;
import java.util.Date;

import jakarta.ws.rs.core.MediaType;

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

/**
 * An extension of {@link FormDataBodyPart} for associating
 * {@link File} File as a body part entity.
 * <p/>
 * This class may be used to create body parts that contains a file attachments.
 * Appropriate Content-Disposition parameters and Content-Type header will be derived from the file.
 *
 * @author Imran M Yousuf (imran at smartitengineering.com)
 * @author Paul Sandoz
 * @author Michal Gajdos
 */
public class FileDataBodyPart extends FormDataBodyPart {

    private File fileEntity;

    private MediaTypePredictor predictor = DefaultMediaTypePredictor.getInstance();

    /**
     * A no-args constructor which expects its client to set the values
     * individually, the attributes to be set are fileEntity and name; the
     * media type will be predicted from the fileEntity if not set explicitly.
     */
    public FileDataBodyPart() {
        super();
    }

    /**
     * Constructs the body part with the provided name and file, it predicts the
     * {@link MediaType} of the file provided. For the known media types client
     * will not need to set the media type explicitly.
     *
     * @param name the name of body part.
     * @param fileEntity the file that represents the entity.
     *
     * @see MediaTypePredictor#getMediaTypeFromFile(java.io.File)
     * @see FileDataBodyPart#FileDataBodyPart(java.lang.String, java.io.File, jakarta.ws.rs.core.MediaType)
     */
    public FileDataBodyPart(final String name, final File fileEntity) {
        this(name, fileEntity, null);
    }

    /**
     * Constructs the body part with all the attributes set for its proper
     * function. If this constructor is used to construct the body part then it
     * is not required to set any other attributes for proper behavior.
     *
     * @param name the name of body part.
     * @param fileEntity the file that represents the entity.
     * @param mediaType the {@link MediaType} of the body part.
     * @throws java.lang.IllegalArgumentException if the fileEntity is {@code null}.
     */
    public FileDataBodyPart(final String name, final File fileEntity, final MediaType mediaType) throws IllegalArgumentException {
        super();
        super.setName(name);

        if (mediaType != null) {
            setFileEntity(fileEntity, mediaType);
        } else {
            setFileEntity(fileEntity, predictMediaType(fileEntity));
        }
    }

    /**
     * Gets the file for this body part.
     *
     * @return file entity for this body part.
     */
    public File getFileEntity() {
        return fileEntity;
    }

    /**
     * This operation is not supported from this implementation.
     *
     * @param mediaType the media type for this field value.
     * @param value the field value as a Java object.
     * @throws java.lang.UnsupportedOperationException Operation not supported.
     *
     * @see FileDataBodyPart#setFileEntity(java.io.File, jakarta.ws.rs.core.MediaType)
     */
    @Override
    public void setValue(final MediaType mediaType, final Object value) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("It is unsupported, please use setFileEntity instead!");
    }

    /**
     * This operation is not supported from this implementation.
     *
     * @param entity the new entity object.
     * @throws java.lang.UnsupportedOperationException Operation not supported.
     *
     * @see FileDataBodyPart#setFileEntity(java.io.File)
     */
    @Override
    public void setEntity(final Object entity) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("It is unsupported, please use setFileEntity instead!");
    }

    /**
     * Sets the fileEntity for this {@link FormDataBodyPart}.
     *
     * @param fileEntity the entity of this {@link FormDataBodyPart}.
     */
    public void setFileEntity(final File fileEntity) {
        this.setFileEntity(fileEntity, predictMediaType());
    }

    /**
     * Sets the {@link MediaType} and fileEntity for this {@link FormDataBodyPart}.
     *
     * @param fileEntity the entity of this body part.
     * @param mediaType the media type.
     */
    public void setFileEntity(final File fileEntity, final MediaType mediaType) {
        super.setMediaType(mediaType);

        super.setEntity(fileEntity);
        this.fileEntity = fileEntity;

        if (fileEntity != null) {
            FormDataContentDisposition.FormDataContentDispositionBuilder builder =
                    FormDataContentDisposition.name(getName());
            builder.fileName(fileEntity.getName());
            if (fileEntity.exists()) {
                builder.size(fileEntity.length());
                builder.modificationDate(new Date(fileEntity.lastModified()));
            }
            setFormDataContentDisposition(builder.build());
        }
    }

    /**
     * Predicts the media type of the current fileEntity.
     *
     * @return predicted {@link MediaType}.
     */
    protected MediaType predictMediaType() {
        return predictMediaType(getFileEntity());
    }

    /**
     * Predicts the media type of the provided {@link File}.
     *
     * @param file the file from which the media type is predicted.
     * @return predicted {@link MediaType}.
     */
    protected MediaType predictMediaType(final File file) {
        return getPredictor().getMediaTypeFromFile(file);
    }

    /**
     * Gets the media type predictor.
     *
     * @return the media type predictor.
     */
    public MediaTypePredictor getPredictor() {
        return predictor;
    }

    /**
     * Sets the media type predictor.
     *
     * @param predictor the media type predictor.
     */
    public void setPredictor(MediaTypePredictor predictor) {
        if (predictor == null) {
            throw new IllegalArgumentException();
        }

        this.predictor = predictor;
    }

}
