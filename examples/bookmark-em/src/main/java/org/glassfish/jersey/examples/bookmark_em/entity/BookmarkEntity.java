/*
 * Copyright (c) 2012, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.glassfish.jersey.examples.bookmark_em.entity;

import java.io.Serializable;
import java.util.Date;

import jakarta.annotation.ManagedBean;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/**
 * Entity class BookmarkEntity.
 *
 * @author Jakub Podlesak
 */
@Entity
@Table(name = "BOOKMARKS")
@NamedQueries({
        @NamedQuery(name = "BookmarkEntity.findByUserid",
                query = "SELECT b FROM BookmarkEntity b WHERE b.bookmarkEntityPK.userid = :userid"),
        @NamedQuery(name = "BookmarkEntity.findByBmid",
                query = "SELECT b FROM BookmarkEntity b WHERE b.bookmarkEntityPK.bmid = :bmid"),
        @NamedQuery(name = "BookmarkEntity.findByUri", query = "SELECT b FROM BookmarkEntity b WHERE b.uri = :uri"),
        @NamedQuery(name = "BookmarkEntity.findByUpdated", query = "SELECT b FROM BookmarkEntity b WHERE b.updated = :updated"),
        @NamedQuery(name = "BookmarkEntity.findByLdesc", query = "SELECT b FROM BookmarkEntity b WHERE b.ldesc = :ldesc"),
        @NamedQuery(name = "BookmarkEntity.findBySdesc", query = "SELECT b FROM BookmarkEntity b WHERE b.sdesc = :sdesc")
})
@ManagedBean
@SuppressWarnings("UnusedDeclaration")
public class BookmarkEntity implements Serializable {

    /**
     * EmbeddedId primary key field
     */
    @EmbeddedId
    protected BookmarkEntityPK bookmarkEntityPK;

    @Column(name = "URI", nullable = false)
    private String uri;

    @Column(name = "UPDATED")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updated;

    @Column(name = "LDESC")
    private String ldesc;

    @Column(name = "SDESC")
    private String sdesc;

    @JoinColumn(name = "USERID", referencedColumnName = "USERID", insertable = false, updatable = false)
    @ManyToOne
    private UserEntity userEntity;

    /**
     * Creates a new instance of BookmarkEntity
     */
    public BookmarkEntity() {
    }

    /**
     * Creates a new instance of BookmarkEntity with the specified values.
     *
     * @param bookmarkEntityPK the bookmarkEntityPK of the BookmarkEntity
     */
    public BookmarkEntity(BookmarkEntityPK bookmarkEntityPK) {
        this.bookmarkEntityPK = bookmarkEntityPK;
    }

    /**
     * Creates a new instance of BookmarkEntity with the specified values.
     *
     * @param bookmarkEntityPK the bookmarkEntityPK of the BookmarkEntity
     * @param uri the uri of the BookmarkEntity
     */
    public BookmarkEntity(BookmarkEntityPK bookmarkEntityPK, String uri) {
        this.bookmarkEntityPK = bookmarkEntityPK;
        this.uri = uri;
    }

    /**
     * Creates a new instance of BookmarkEntityPK with the specified values.
     *
     * @param bmid the bmid of the BookmarkEntityPK
     * @param userid the userid of the BookmarkEntityPK
     */
    public BookmarkEntity(String bmid, String userid) {
        this.bookmarkEntityPK = new BookmarkEntityPK(bmid, userid);
    }

    /**
     * Gets the bookmarkEntityPK of this BookmarkEntity.
     *
     * @return the bookmarkEntityPK
     */
    public BookmarkEntityPK getBookmarkEntityPK() {
        return this.bookmarkEntityPK;
    }

    /**
     * Sets the bookmarkEntityPK of this BookmarkEntity to the specified value.
     *
     * @param bookmarkEntityPK the new bookmarkEntityPK
     */
    public void setBookmarkEntityPK(BookmarkEntityPK bookmarkEntityPK) {
        this.bookmarkEntityPK = bookmarkEntityPK;
    }

    /**
     * Gets the uri of this BookmarkEntity.
     *
     * @return the uri
     */
    public String getUri() {
        return this.uri;
    }

    /**
     * Sets the uri of this BookmarkEntity to the specified value.
     *
     * @param uri the new uri
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    /**
     * Gets the updated of this BookmarkEntity.
     *
     * @return the updated
     */
    public Date getUpdated() {
        return this.updated;
    }

    /**
     * Sets the updated of this BookmarkEntity to the specified value.
     *
     * @param updated the new updated
     */
    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    /**
     * Gets the ldesc of this BookmarkEntity.
     *
     * @return the ldesc
     */
    public String getLdesc() {
        return this.ldesc;
    }

    /**
     * Sets the ldesc of this BookmarkEntity to the specified value.
     *
     * @param ldesc the new ldesc
     */
    public void setLdesc(String ldesc) {
        this.ldesc = ldesc;
    }

    /**
     * Gets the sdesc of this BookmarkEntity.
     *
     * @return the sdesc
     */
    public String getSdesc() {
        return this.sdesc;
    }

    /**
     * Sets the sdesc of this BookmarkEntity to the specified value.
     *
     * @param sdesc the new sdesc
     */
    public void setSdesc(String sdesc) {
        this.sdesc = sdesc;
    }

    /**
     * Gets the userEntity of this BookmarkEntity.
     *
     * @return the userEntity
     */
    public UserEntity getUserEntity() {
        return this.userEntity;
    }

    /**
     * Sets the userEntity of this BookmarkEntity to the specified value.
     *
     * @param userEntity the new userEntity
     */
    public void setUserEntity(UserEntity userEntity) {
        this.userEntity = userEntity;
    }

    /**
     * Returns a hash code value for the object.  This implementation computes
     * a hash code value based on the id fields in this object.
     *
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        int hash = 0;
        hash += (this.bookmarkEntityPK != null ? this.bookmarkEntityPK.hashCode() : 0);
        return hash;
    }

    /**
     * Determines whether another object is equal to this BookmarkEntity.  The result is
     * <code>true</code> if and only if the argument is not null and is a BookmarkEntity object that
     * has the same id field values as this object.
     *
     * @param object the reference object with which to compare
     * @return <code>true</code> if this object is the same as the argument;
     *         <code>false</code> otherwise.
     */
    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof BookmarkEntity)) {
            return false;
        }
        BookmarkEntity other = (BookmarkEntity) object;
        return !(this.bookmarkEntityPK != other.bookmarkEntityPK && (this.bookmarkEntityPK == null || !this.bookmarkEntityPK
                .equals(other.bookmarkEntityPK)));
    }

    /**
     * Returns a string representation of the object.  This implementation constructs
     * that representation based on the id fields.
     *
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        return "BookmarkEntity{"
               + "bookmarkEntityPK=" + bookmarkEntityPK
               + '}';
    }
}
