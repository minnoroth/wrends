HTTP/1.1 200 OK
X-Powered-By: Servlet/2.5
Server: Sun Java System Application Server 9.1
Content-Type: text/xml
Date: Wed, 28 Nov 2007 15:00:33 GMT
Connection: close

<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
<SOAP-ENV:Body>
<dsml:batchResponse xmlns:dsml="urn:oasis:names:tc:DSML:2:0:core" requestID="[addRequest] start element is mispelled">
<dsml:errorResponse type="malformedRequest">
<dsml:message>
javax.xml.transform.TransformerException: org.xml.sax.SAXParseException: The element type "add_BOGUS" must be terminated by the matching end-tag "&lt;/add_BOGUS&gt;".</dsml:message>
</dsml:errorResponse>
</dsml:batchResponse>
</SOAP-ENV:Body>
</SOAP-ENV:Envelope>

