/*******************************************************************************
 *  Imixs Workflow Technology
 *  Copyright (C) 2001, 2008 Imixs Software Solutions GmbH,  
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
 *  Contributors:  
 *  	Imixs Software Solutions GmbH - initial API and implementation
 *  	Ralph Soika
 *******************************************************************************/
package org.imixs.archive.signature.ca;

import javax.ejb.Singleton;

/**
 * The CAService provides methods to managed X509Certificates stored in a keystore. The certificates 
 * are  used for
 * digital Signature only. Certificates are singed by an existing root or
 * intermediate Certificate stored in a keystore.
 * <p>
 * Certificates generated by this service have empty passwords and protected by
 * the keystore. For that reason a certificate managed by this service should
 * never be published and used for internal digital signatures only
 * <p>
 * The service is implemented as a singleton to avoid concurrent access from
 * different clients.
 * 
 * @see X509CertificateGenerator
 * @author rsoika
 * @version 1.0
 */
@Singleton
public class CAService {

	
	
	
}
