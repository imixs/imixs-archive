/*******************************************************************************
 *  Imixs Workflow 
 *  Copyright (C) 2001, 2011 Imixs Software Solutions GmbH,  
 *  http://www.imixs.com
 *  
 *  This program is free software; you can redistribute it and/or 
 *  modify it under the terms of the GNU General Public License 
 *  as published by the Free Software Foundation; either version 2 
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful, 
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of 
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 *  General Public License for more details.
 *  
 *  You can receive a copy of the GNU General Public
 *  License at http://www.gnu.org/licenses/gpl.html
 *  
 *  Project: 
 *  	http://www.imixs.org
 *  	http://java.net/projects/imixs-workflow
 *  
 *  Contributors:  
 *  	Imixs Software Solutions GmbH - initial API and implementation
 *  	Ralph Soika - Software Developer
 *******************************************************************************/

package org.imixs.archive.signature.ca;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.exceptions.QueryException;

/**
 * This X509ProfileHandler is a CDI bean to lookup the X509 profile data for a
 * given alias name. This CDI bean can be overwritten with an alternative by
 * individual implementations. E.g. a LDAP lookup can be performed to lookup the
 * X509 data.
 * 
 * @author rsoika
 * 
 */
@Named
@RequestScoped
public class X509ProfileHandler implements Serializable {

    public final static String ENV_SIGNATURE_X509_PROFILE_QUERY = "signature.x509.profile.query";

    @EJB
    protected DocumentService documentService;

    @Inject
    @ConfigProperty(name = ENV_SIGNATURE_X509_PROFILE_QUERY, defaultValue = "(type:\"workitem\") AND (name:\"?\")")
    Optional<String> profileQuery;

    private static final long serialVersionUID = 1L;

    private static Logger logger = Logger.getLogger(X509ProfileHandler.class.getName());

    public X509ProfileHandler() {
        super();
    }

    /**
     * The lookups a user profile by the default lucene quey. The method can be
     * overwritten by an alternative implementation of this CDI bean.
     * 
     */
    public ItemCollection findX509Profile(String alias) {

        List<ItemCollection> result;
        try {
            result = documentService.find(profileQuery.get(), 1, 0);

            if (result.size() > 0) {
                return result.get(0);
            }
        } catch (QueryException e) {
            logger.severe("Invalid search query: " + profileQuery.get());
        }

        // not found!
        logger.warning("X509 Profile Data not found for alias '" + alias + "'");
        return null;
    }

}
