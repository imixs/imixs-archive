package org.imixs.archive.documents.einvoice;

/**
 * Defines the valid BPMN namespaces.
 * <p>
 * NOTE: The primary namespace can be either 'bpmn2' or 'bpmn' ! But 'bpmn2' is
 * the default.
 * 
 * 
 * 
 * // xmlns:a='urn:un:unece:uncefact:data:standard:QualifiedDataType:100'
 * // xmlns:rsm='urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100'
 * // xmlns:qdt='urn:un:unece:uncefact:data:standard:QualifiedDataType:10'
 * //
 * xmlns:ram='urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100'
 * // xmlns:xs='http://www.w3.org/2001/XMLSchema'
 * // xmlns:udt='urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100'>
 * 
 * @author rsoika
 */
public enum EInvoiceNS {
    CBC, //
    CAC, //
    RSM, //
    QDT, //
    RAM, //
    UDT;
}
